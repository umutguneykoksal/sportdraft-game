package dev.sportdraft.bidding.control;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.boundary.PlaceBidRequest;
import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.RoundStatus;
import dev.sportdraft.session.control.SessionAccess;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveBid;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuctionRoundLifecycle {

    private final ActiveSessionStore sessionStore;
    private final CardCatalogService cardCatalog;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final MeterRegistry meterRegistry;
    private final AuctionSettlementService settlement;
    private final AuctionMatchTransition matchTransition;
    private final AuctionTurnNormalizer turnNormalizer;
    private final AuctionPassHandler passHandler;

    public AuctionRoundLifecycle(
        ActiveSessionStore sessionStore,
        CardCatalogService cardCatalog,
        SseEmitterRegistry sseEmitterRegistry,
        MeterRegistry meterRegistry,
        AuctionSettlementService settlement,
        AuctionMatchTransition matchTransition,
        AuctionPassHandler passHandler
    ) {
        this.sessionStore = sessionStore;
        this.cardCatalog = cardCatalog;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.meterRegistry = meterRegistry;
        this.settlement = settlement;
        this.matchTransition = matchTransition;
        this.turnNormalizer = new AuctionTurnNormalizer(settlement);
        this.passHandler = passHandler;
    }

    AuctionRoundResponse startRound(ActiveSession session) {
        UUID sessionId = session.getId();
        long vBefore = session.getVersion();

        if (session.getStatus() != SessionStatus.AUCTIONING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not auctioning");
        }
        session.findOpenRound().ifPresent(r -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active round already exists");
        });
        if (!AuctionRules.hasFullLobby(session)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot start auction until lobby is full");
        }
        if (AuctionRules.isSessionRostersFull(session)) {
            matchTransition.enterMatchStartPending(session, vBefore);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rosters complete; confirm match start");
        }

        Map<UUID, Card> cardMap = cardCatalog.getCardMap();
        List<UUID> pool = session.getCardPool();

        if (pool.isEmpty()) {
            pool = new java.util.ArrayList<>(
                cardCatalog.getCardIdsForPool(session.getPoolLeague(), session.getMinPoolRating())
            );
            Collections.shuffle(pool);
            session.setCardPool(pool);
            session.setCardPoolIndex(0);
        }

        ActiveRound createdRound = null;
        int poolSize = pool.size();
        int attempts = 0;
        while (attempts < poolSize) {
            RoundCardSelector.SelectionResult selection = RoundCardSelector.findNextEligibleRound(session, cardMap);
            if (selection == null) {
                break;
            }
            session.setCardPoolIndex(selection.nextPoolIndex());
            ActiveRound round = selection.round();
            session.getRounds().add(round);
            if (normalizeTurnForRound(round, session, cardMap)) {
                settlement.finalizeSettledRound(round, session, vBefore, cardMap, false);
                vBefore = session.getVersion();
                attempts++;
                continue;
            }
            createdRound = round;
            break;
        }

        if (createdRound == null) {
            if (AuctionRules.isSessionRostersFull(session)) {
                matchTransition.enterMatchStartPending(session, vBefore);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Rosters complete; confirm match start");
            }
            session.moveToFinalizing(session.getId().getMostSignificantBits());
            sessionStore.saveIfVersionMatch(session, vBefore);
            matchTransition.publishSessionFinalized(session, cardMap);
            sseEmitterRegistry.broadcast(
                sessionId, "session_finalizing",
                Map.of("sessionId", sessionId, "reason", "no_eligible_cards")
            );
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No eligible cards remain, session finalizing");
        }

        sessionStore.saveIfVersionMatch(session, vBefore);
        Card roundCard = cardMap.get(createdRound.getCardId());
        AuctionRoundResponse response = AuctionRoundResponses.toResponse(createdRound, roundCard, session, cardMap);
        sseEmitterRegistry.broadcast(sessionId, "round_started", Map.of("round", response));
        meterRegistry.counter("auction.round.started").increment();
        return response;
    }

    AuctionRoundResponse placeBid(UUID sessionId, UUID userId, String idempotencyKey, PlaceBidRequest request) {
        ActiveSession session = sessionStore.getById(sessionId);
        long vBefore = session.getVersion();

        ActiveRound round = session.findRoundById(request.roundId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));
        if (session.getStatus() != SessionStatus.AUCTIONING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not auctioning");
        }
        if (!AuctionRules.hasFullLobby(session)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session does not have a full lobby");
        }

        Map<UUID, Card> cardMap = cardCatalog.getCardMap();
        if (AuctionRules.isSessionRostersFull(session)) {
            matchTransition.enterMatchStartPending(session, vBefore);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rosters complete; confirm match start");
        }
        if (round.getStatus() != RoundStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open");
        }
        Instant graceDeadline = round.getEndsAt().plusMillis(AuctionTiming.BID_GRACE_PERIOD_MS);
        if (Instant.now().isAfter(graceDeadline)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round has already ended");
        }

        UUID idem = AuctionRules.parseUuid(idempotencyKey, "Invalid Idempotency-Key value");
        Card roundCard = cardMap.get(round.getCardId());
        if (round.hasBidWithIdempotencyKey(idem)) {
            return AuctionRoundResponses.toResponseWithContext(round, roundCard, session, cardMap, userId);
        }

        SessionAccess.requireMember(session, userId);
        ActivePlayer bidder = session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not found in session"));
        if (normalizeTurnForRound(round, session, cardMap)) {
            settlement.finalizeSettledRound(round, session, vBefore, cardMap, true);
            meterRegistry.counter("auction.bid.placed").increment();
            return AuctionRoundResponses.resolveMutationResponse(session, cardMap, userId, roundCard, round);
        }
        AuctionBidValidator.validateBid(session, round, bidder, roundCard, request, cardMap, userId);

        Instant newEndsAt = round.getEndsAt();
        if (Instant.now().isAfter(round.getEndsAt().minus(AuctionTiming.ANTI_SNIPE_WINDOW_SECONDS, ChronoUnit.SECONDS))) {
            newEndsAt = round.getEndsAt().plus(AuctionTiming.ANTI_SNIPE_EXTENSION_SECONDS, ChronoUnit.SECONDS);
        }

        UUID nextTurn = AuctionRules.otherPlayerUserId(session, userId);
        ActiveBid bid = new ActiveBid(UUID.randomUUID(), bidder.getId(), request.amountCents(), idem, Instant.now());
        round.getBids().add(bid);
        round.registerBid(bidder.getId(), request.amountCents(), newEndsAt, nextTurn);
        normalizeTurnForRound(round, session, cardMap);

        if (round.getStatus() != RoundStatus.OPEN) {
            settlement.finalizeSettledRound(round, session, vBefore, cardMap, true);
            meterRegistry.counter("auction.bid.placed").increment();
            return AuctionRoundResponses.resolveMutationResponse(session, cardMap, userId, roundCard, round);
        }

        sessionStore.saveIfVersionMatch(session, vBefore);

        AuctionRoundResponse response = AuctionRoundResponses.toResponseWithContext(round, roundCard, session, cardMap, userId);
        sseEmitterRegistry.broadcast(
            sessionId, "bid_placed",
            Map.of(
                "round", AuctionRoundResponses.toResponse(round, roundCard, session, cardMap),
                "userId", userId,
                "amountCents", request.amountCents()
            )
        );
        meterRegistry.counter("auction.bid.placed").increment();
        return response;
    }

    AuctionRoundResponse readyNext(UUID sessionId, UUID userId, UUID roundId) {
        ActiveSession session = sessionStore.getById(sessionId);
        long vBefore = session.getVersion();
        Map<UUID, Card> cardMap = cardCatalog.getCardMap();

        SessionAccess.requireMember(session, userId);

        ActiveRound round = session.findRoundById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (round.getStatus() != RoundStatus.OPEN) {
            return session.findOpenRound()
                .map(r -> AuctionRoundResponses.toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open"));
        }
        if (round.getHighestBidderId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ready-next is only allowed when there is no bid on the round");
        }

        round.registerReady(userId);
        if (round.areAllReady(session.getMaxPlayers())) {
            settlement.closeRound(round, session, vBefore, cardMap);
            return session.findOpenRound()
                .map(r -> AuctionRoundResponses.toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
                .orElse(AuctionRoundResponses.toResponse(round, cardMap.get(round.getCardId()), session, cardMap));
        }
        sessionStore.saveIfVersionMatch(session, vBefore);
        sseEmitterRegistry.broadcast(
            sessionId, "round_ready",
            Map.of("round", AuctionRoundResponses.toResponse(round, cardMap.get(round.getCardId()), session, cardMap), "userId", userId)
        );
        return AuctionRoundResponses.toResponseWithContext(round, cardMap.get(round.getCardId()), session, cardMap, userId);
    }

    AuctionRoundResponse passRound(UUID sessionId, UUID userId, UUID roundId) {
        ActiveSession session = sessionStore.getById(sessionId);
        long vBefore = session.getVersion();
        Map<UUID, Card> cardMap = cardCatalog.getCardMap();

        SessionAccess.requireMember(session, userId);

        ActiveRound round = session.findRoundById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (round.getStatus() != RoundStatus.OPEN) {
            return session.findOpenRound()
                .map(r -> AuctionRoundResponses.toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open"));
        }

        Card roundCard = cardMap.get(round.getCardId());
        if (normalizeTurnForRound(round, session, cardMap)) {
            settlement.finalizeSettledRound(round, session, vBefore, cardMap, true);
            return AuctionRoundResponses.resolveMutationResponse(session, cardMap, userId, roundCard, round);
        }
        boolean turnOpen = AuctionRules.isTurnOpen(round);
        if (!turnOpen && round.getCurrentTurnUserId() != null && !round.getCurrentTurnUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not your turn");
        }

        AuctionPassHandler.PassRoundResult result = passHandler.pass(session, vBefore, userId, round, cardMap);
        if (result.broadcastTurnPassed()) {
            sseEmitterRegistry.broadcast(sessionId, "turn_passed",
                Map.of("round", AuctionRoundResponses.toResponse(round, roundCard, session, cardMap), "userId", userId));
        }
        return result.response();
    }

    void maybeStartNextRound(ActiveSession session) {
        if (session.getStatus() != SessionStatus.AUCTIONING) {
            return;
        }
        if (AuctionRules.isSessionRostersFull(session)) {
            matchTransition.enterMatchStartPending(session, session.getVersion());
            return;
        }
        if (session.findOpenRound().isPresent()) {
            return;
        }
        startRound(session);
    }

    void ensureOpenRound(ActiveSession session) {
        maybeStartNextRound(session);
    }

    boolean normalizeTurnForRound(ActiveRound round, ActiveSession session, Map<UUID, Card> cardMap) {
        return turnNormalizer.normalizeTurnForRound(round, session, cardMap);
    }
}
