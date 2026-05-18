package com.example.auction.bidding.control;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.AuctionStateResponse;
import com.example.auction.bidding.boundary.AuctionTeamCardResponse;
import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.PlayerAttributes;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveBid;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.session.redis.SessionMutationGuard;
import com.example.auction.simulation.control.SessionFinalizedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuctionService {

    private static final long MIN_INCREMENT = 1L;
    private static final long STARTING_BID = 1L;
    private static final int ROSTER_SIZE = 11;
    private static final long MATCH_START_COUNTDOWN_SECONDS = 20L;
    private static final long ANTI_SNIPE_WINDOW_SECONDS = 10L;  // Bid in last 10 seconds triggers extension
    private static final long ANTI_SNIPE_EXTENSION_SECONDS = 10L;  // Extends by 10 seconds (no max limit)
    private static final long ROUND_DURATION_SECONDS = 30L;
    /** Grace period after endsAt — late bids accepted; round closes only after this elapses. */
    private static final long BID_GRACE_PERIOD_MS = 1500L;
    private static final long TURN_OPEN_AFTER_SECONDS = 15L;

    private final ActiveSessionStore sessionStore;
    private final SessionMutationGuard mutationGuard;
    private final CardCatalogService cardCatalog;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ApplicationEventPublisher publisher;
    private final MeterRegistry meterRegistry;

    public AuctionService(
        ActiveSessionStore sessionStore,
        SessionMutationGuard mutationGuard,
        CardCatalogService cardCatalog,
        SseEmitterRegistry sseEmitterRegistry,
        ApplicationEventPublisher publisher,
        MeterRegistry meterRegistry
    ) {
        this.sessionStore = sessionStore;
        this.mutationGuard = mutationGuard;
        this.cardCatalog = cardCatalog;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
    }

    private Map<UUID, Card> loadCardMap() {
        return cardCatalog.getCardMap();
    }

    private Card getCard(UUID cardId) {
        return cardCatalog.getCard(cardId);
    }

    // --- Public API ---

    public void confirmMatchStart(UUID sessionId, UUID userId) {
        mutationGuard.mutateVoid(sessionId, () -> confirmMatchStartInternal(sessionId, userId));
    }

    private void confirmMatchStartInternal(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.getById(sessionId);
        session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not in session"));

        long vBefore = session.getVersion();
        if (session.getStatus() != SessionStatus.MATCH_START_PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match start is not pending");
        }
        session.registerMatchStartReady(userId);
        if (session.areBothMatchStartReady()) {
            finalizeSessionFromFullRosters(session, vBefore);
        } else {
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "match_start_ready",
                Map.of("sessionId", sessionId, "readyCount", session.matchStartReadyCount(), "userId", userId)
            );
        }
    }

    public AuctionRoundResponse startRound(UUID sessionId) {
        return mutationGuard.mutate(sessionId, () -> startRoundInternal(sessionStore.getById(sessionId)));
    }

    private AuctionRoundResponse startRoundInternal(ActiveSession session) {
        UUID sessionId = session.getId();
        long vBefore = session.getVersion();

        if (session.getStatus() != SessionStatus.AUCTIONING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not auctioning");
        }
        session.findOpenRound().ifPresent(r -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active round already exists");
        });
        if (session.playerCount() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot start auction round without two players");
        }
        if (isSessionRostersFull(session)) {
            enterMatchStartPending(session, vBefore);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rosters complete; confirm match start");
        }

        Map<UUID, Card> cardMap = loadCardMap();
        List<UUID> pool = session.getCardPool();
        java.util.Set<UUID> sold = session.soldCardIds();

        if (pool.isEmpty()) {
            pool = new java.util.ArrayList<>(cardMap.keySet());
            Collections.shuffle(pool);
            session.setCardPool(pool);
            session.setCardPoolIndex(0);
        }

        ActiveRound createdRound = null;
        int poolSize = pool.size();
        int checked = 0;
        int idx = session.getCardPoolIndex();

        while (checked < poolSize) {
            if (idx >= poolSize) idx = 0;
            UUID cardId = pool.get(idx);
            idx++;
            checked++;

            if (sold.contains(cardId)) continue;

            Card card = cardMap.get(cardId);
            if (card == null) continue;

            boolean anyEligible = session.getPlayers().stream()
                .anyMatch(p -> RosterSlotAssignment.canFillCard(p, card, cardMap));
            if (!anyEligible) continue;

            Instant now = Instant.now();
            UUID openingBidder = determineOpeningBidder(session, cardMap);
            ActiveRound round = new ActiveRound(
                UUID.randomUUID(), card.getId(), now, now.plus(ROUND_DURATION_SECONDS, ChronoUnit.SECONDS),
                openingBidder, RoundStatus.OPEN
            );
            session.getRounds().add(round);
            if (normalizeTurnForRound(round, session, cardMap)) {
                finalizeSettledRound(round, session, vBefore, cardMap, false);
                vBefore = session.getVersion();
                continue;
            }

            createdRound = round;
            session.setCardPoolIndex(idx);
            break;
        }

        if (createdRound == null) {
            if (isSessionRostersFull(session)) {
                enterMatchStartPending(session, vBefore);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Rosters complete; confirm match start");
            }
            session.moveToFinalizing(session.getId().getMostSignificantBits());
            sessionStore.saveIfVersionMatch(session, vBefore);
            publishSessionFinalized(session, cardMap);
            sseEmitterRegistry.broadcast(
                sessionId, "session_finalizing",
                Map.of("sessionId", sessionId, "reason", "no_eligible_cards")
            );
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No eligible cards remain, session finalizing");
        }

        sessionStore.saveIfVersionMatch(session, vBefore);
        Card roundCard = cardMap.get(createdRound.getCardId());
        AuctionRoundResponse response = toResponse(createdRound, roundCard, session, cardMap);
        sseEmitterRegistry.broadcast(sessionId, "round_started", Map.of("round", response));
        meterRegistry.counter("auction.round.started").increment();
        return response;
    }

    public AuctionRoundResponse placeBid(UUID sessionId, UUID userId, String idempotencyKey, PlaceBidRequest request) {
        return mutationGuard.mutate(sessionId, () -> placeBidInternal(sessionId, userId, idempotencyKey, request));
    }

    private AuctionRoundResponse placeBidInternal(UUID sessionId, UUID userId, String idempotencyKey, PlaceBidRequest request) {
        ActiveSession session = sessionStore.getById(sessionId);
        long vBefore = session.getVersion();

        ActiveRound round = session.findRoundById(request.roundId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));
        if (session.getStatus() != SessionStatus.AUCTIONING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not auctioning");
        }
        if (session.playerCount() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session does not have two players");
        }

        Map<UUID, Card> cardMap = loadCardMap();
        if (isSessionRostersFull(session)) {
            enterMatchStartPending(session, vBefore);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rosters complete; confirm match start");
        }
        if (round.getStatus() != RoundStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open");
        }
        Instant graceDeadline = round.getEndsAt().plusMillis(BID_GRACE_PERIOD_MS);
        if (Instant.now().isAfter(graceDeadline)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round has already ended");
        }

        UUID idem = parseUuid(idempotencyKey, "Invalid Idempotency-Key value");
        Card roundCard = cardMap.get(round.getCardId());
        if (round.hasBidWithIdempotencyKey(idem)) {
            return toResponseWithContext(round, roundCard, session, cardMap, userId);
        }

        ActivePlayer bidder = session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));
        if (bidder.isRosterFull()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Roster is full");
        }
        if (normalizeTurnForRound(round, session, cardMap)) {
            finalizeSettledRound(round, session, vBefore, cardMap, true);
            return resolveMutationResponse(session, cardMap, userId, roundCard, round);
        }
        boolean turnOpen = isTurnOpen(round);
        if (!turnOpen && round.getCurrentTurnUserId() != null && !round.getCurrentTurnUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not your turn to bid");
        }

        long minimumAllowed = round.getHighestBidCents() == 0 ? STARTING_BID : round.getHighestBidCents() + MIN_INCREMENT;
        if (request.amountCents() < minimumAllowed) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Bid is below minimum increment");
        }
        if (request.amountCents() > bidder.getRemainingBudgetCents()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Insufficient budget. Maximum bid: %d cr".formatted(bidder.getRemainingBudgetCents()));
        }
        if (!RosterSlotAssignment.canFillCard(bidder, roundCard, cardMap)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No open slot for this card");
        }
        int totalOpen = RosterSlotAssignment.totalOpenSlots(bidder, cardMap);
        long remainingAfterBid = bidder.getRemainingBudgetCents() - request.amountCents();
        long reserveRequired = Math.max(totalOpen - 1, 0);
        if (remainingAfterBid < reserveRequired) {
            long maxAllowed = bidder.getRemainingBudgetCents() - reserveRequired;
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Must reserve %d cr for %d remaining slots. Maximum bid: %d cr"
                    .formatted(reserveRequired, Math.max(totalOpen - 1, 0), maxAllowed));
        }

        Instant newEndsAt = round.getEndsAt();
        if (Instant.now().isAfter(round.getEndsAt().minus(ANTI_SNIPE_WINDOW_SECONDS, ChronoUnit.SECONDS))) {
            // Bid in last 10 seconds → extend by 10 seconds (no maximum limit)
            newEndsAt = round.getEndsAt().plus(ANTI_SNIPE_EXTENSION_SECONDS, ChronoUnit.SECONDS);
        }

        UUID nextTurn = otherPlayerUserId(session, userId);
        ActiveBid bid = new ActiveBid(UUID.randomUUID(), bidder.getId(), request.amountCents(), idem, Instant.now());
        round.getBids().add(bid);
        round.registerBid(bidder.getId(), request.amountCents(), newEndsAt, nextTurn);
        normalizeTurnForRound(round, session, cardMap);

        if (round.getStatus() != RoundStatus.OPEN) {
            finalizeSettledRound(round, session, vBefore, cardMap, true);
            meterRegistry.counter("auction.bid.placed").increment();
            return resolveMutationResponse(session, cardMap, userId, roundCard, round);
        }

        sessionStore.saveIfVersionMatch(session, vBefore);

        AuctionRoundResponse response = toResponseWithContext(round, roundCard, session, cardMap, userId);
        sseEmitterRegistry.broadcast(
            sessionId, "bid_placed",
            Map.of("round", toResponse(round, roundCard, session, cardMap), "userId", userId, "amountCents", request.amountCents())
        );
        meterRegistry.counter("auction.bid.placed").increment();
        return response;
    }

    public AuctionRoundResponse readyNext(UUID sessionId, UUID userId, UUID roundId) {
        return mutationGuard.mutate(sessionId, () -> readyNextInternal(sessionId, userId, roundId));
    }

    private AuctionRoundResponse readyNextInternal(UUID sessionId, UUID userId, UUID roundId) {
        ActiveSession session = sessionStore.getById(sessionId);
        long vBefore = session.getVersion();
        Map<UUID, Card> cardMap = loadCardMap();

        session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));

        ActiveRound round = session.findRoundById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (round.getStatus() != RoundStatus.OPEN) {
            return session.findOpenRound()
                .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open"));
        }
        if (round.getHighestBidderId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ready-next is only allowed when there is no bid on the round");
        }

        round.registerReady(userId);
        if (round.areBothReady()) {
            closeRound(round, session, vBefore, cardMap);
            return session.findOpenRound()
                .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
                .orElse(toResponse(round, cardMap.get(round.getCardId()), session, cardMap));
        } else {
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "round_ready",
                Map.of("round", toResponse(round, cardMap.get(round.getCardId()), session, cardMap), "userId", userId)
            );
            return toResponseWithContext(round, cardMap.get(round.getCardId()), session, cardMap, userId);
        }
    }

    public AuctionRoundResponse passRound(UUID sessionId, UUID userId, UUID roundId) {
        return mutationGuard.mutate(sessionId, () -> passRoundInternal(sessionId, userId, roundId));
    }

    private AuctionRoundResponse passRoundInternal(UUID sessionId, UUID userId, UUID roundId) {
        ActiveSession session = sessionStore.getById(sessionId);
        long vBefore = session.getVersion();
        Map<UUID, Card> cardMap = loadCardMap();

        session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));

        ActiveRound round = session.findRoundById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (round.getStatus() != RoundStatus.OPEN) {
            return session.findOpenRound()
                .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open"));
        }

        Card roundCard = cardMap.get(round.getCardId());
        if (normalizeTurnForRound(round, session, cardMap)) {
            finalizeSettledRound(round, session, vBefore, cardMap, true);
            return resolveMutationResponse(session, cardMap, userId, roundCard, round);
        }
        boolean turnOpen = isTurnOpen(round);
        if (!turnOpen && round.getCurrentTurnUserId() != null && !round.getCurrentTurnUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not your turn");
        }

        UUID opponentUserId = otherPlayerUserId(session, userId);
        boolean callerIsHighestBidder = round.getHighestBidderId() != null
            && session.findPlayerByUserId(userId)
                .map(p -> p.getId().equals(round.getHighestBidderId())).orElse(false);

        // Highest bidder "ends turn" — just transfers to opponent, no permanent pass
        if (callerIsHighestBidder) {
            if (round.hasUserPassed(opponentUserId)) {
                // Opponent already permanently passed — sell immediately
                closeRound(round, session, vBefore, cardMap);
                return session.findOpenRound()
                    .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
                    .orElse(toResponse(round, roundCard, session, cardMap));
            }
            round.setCurrentTurnUserId(opponentUserId);
            sessionStore.saveIfVersionMatch(session, vBefore);
            AuctionRoundResponse response = toResponseWithContext(round, roundCard, session, cardMap, userId);
            sseEmitterRegistry.broadcast(sessionId, "turn_passed",
                Map.of("round", toResponse(round, roundCard, session, cardMap), "userId", userId));
            return response;
        }

        // Non-highest-bidder passes — permanent exit from this round
        round.registerPass(userId);

        if (round.getHighestBidderId() != null) {
            // Opponent has a standing bid and we passed — sell to them immediately
            closeRound(round, session, vBefore, cardMap);
            return session.findOpenRound()
                .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
                .orElse(toResponse(round, roundCard, session, cardMap));
        }

        // No bids — give turn to opponent if they can bid and haven't also passed
        ActivePlayer opponent = session.findPlayerByUserId(opponentUserId).orElse(null);
        boolean opponentCanBid = opponent != null
            && canPlayerBidRound(opponent, roundCard, STARTING_BID, cardMap);

        if (opponentCanBid && !round.hasUserPassed(opponentUserId)) {
            round.setCurrentTurnUserId(opponentUserId);
            sessionStore.saveIfVersionMatch(session, vBefore);
            AuctionRoundResponse response = toResponseWithContext(round, roundCard, session, cardMap, userId);
            sseEmitterRegistry.broadcast(sessionId, "turn_passed",
                Map.of("round", toResponse(round, roundCard, session, cardMap), "userId", userId));
            return response;
        }

        // Both passed (or opponent can't bid) with no bids — close as PASSED
        closeRound(round, session, vBefore, cardMap);
        return session.findOpenRound()
            .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
            .orElse(toResponse(round, roundCard, session, cardMap));
    }

    public AuctionStateResponse getAuctionState(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.getById(sessionId);
        TeamSnapshot teams = buildTeamSnapshot(session, userId);

        Map<UUID, Card> cardMap = loadCardMap();
        AuctionRoundResponse currentRound = session.findOpenRound()
            .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
            .orElse(null);

        return new AuctionStateResponse(
            currentRound,
            teams.myTeam(),
            teams.opponentTeam(),
            teams.myFormation(),
            teams.opponentFormation(),
            session.getVersion()
        );
    }

    /**
     * Builds perspective-corrected team cards and formations from any ActiveSession
     * (whether live in Redis or deserialized from archive replay_data).
     */
    public TeamSnapshot buildTeamSnapshot(ActiveSession session, UUID userId) {
        Map<UUID, Card> cardMap = loadCardMap();

        if (session.playerCount() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session does not have two players");
        }
        ActivePlayer me = session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));
        ActivePlayer opponent = session.getPlayers().stream()
            .filter(p -> !p.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Opponent not found in session"));

        List<ActiveRound> soldRounds = session.roundsByStatus(RoundStatus.SOLD);
        Map<UUID, Long> soldAmountByCardId = new HashMap<>();
        Map<UUID, Integer> draftOrderByCardId = new HashMap<>();
        int draftIndex = 0;
        for (ActiveRound sr : soldRounds) {
            soldAmountByCardId.put(sr.getCardId(), sr.getHighestBidCents());
            draftOrderByCardId.put(sr.getCardId(), draftIndex++);
        }

        return new TeamSnapshot(
            buildTeamCards(me, cardMap, soldAmountByCardId, draftOrderByCardId),
            buildTeamCards(opponent, cardMap, soldAmountByCardId, draftOrderByCardId),
            me.getFormation().name(),
            opponent.getFormation().name()
        );
    }

    public record TeamSnapshot(
        List<AuctionTeamCardResponse> myTeam,
        List<AuctionTeamCardResponse> opponentTeam,
        String myFormation,
        String opponentFormation
    ) {}

    public SpectateSnapshot buildSpectateSnapshot(ActiveSession session) {
        Map<UUID, Card> cardMap = loadCardMap();
        List<ActivePlayer> sorted = session.playersSortedById();
        if (sorted.size() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session does not have two players");
        }
        ActivePlayer home = sorted.get(0);
        ActivePlayer away = sorted.get(1);

        List<ActiveRound> soldRounds = session.roundsByStatus(RoundStatus.SOLD);
        Map<UUID, Long> soldAmountByCardId = new HashMap<>();
        Map<UUID, Integer> draftOrderByCardId = new HashMap<>();
        int draftIndex = 0;
        for (ActiveRound sr : soldRounds) {
            soldAmountByCardId.put(sr.getCardId(), sr.getHighestBidCents());
            draftOrderByCardId.put(sr.getCardId(), draftIndex++);
        }

        AuctionRoundResponse currentRound = session.findOpenRound()
            .map(r -> toResponse(r, cardMap.get(r.getCardId()), session, cardMap))
            .orElse(null);

        return new SpectateSnapshot(
            session.getStatus().name(),
            currentRound,
            buildTeamCards(home, cardMap, soldAmountByCardId, draftOrderByCardId),
            buildTeamCards(away, cardMap, soldAmountByCardId, draftOrderByCardId),
            home.getFormation().name(),
            away.getFormation().name(),
            home.getNickname(),
            away.getNickname(),
            home.getRemainingBudgetCents(),
            away.getRemainingBudgetCents(),
            session.getHomeScore() != null ? session.getHomeScore() : 0,
            session.getAwayScore() != null ? session.getAwayScore() : 0
        );
    }

    public record SpectateSnapshot(
        String status,
        AuctionRoundResponse currentRound,
        List<AuctionTeamCardResponse> homeTeam,
        List<AuctionTeamCardResponse> awayTeam,
        String homeFormation,
        String awayFormation,
        String homeNickname,
        String awayNickname,
        long homeBudget,
        long awayBudget,
        int homeScore,
        int awayScore
    ) {}

    // --- Scheduled jobs ---

    /**
     * Scheduler for AUCTIONING sessions — timer-driven only (HTTP-event-driven model).
     * Round logic (bid/pass/turn) runs exclusively on REST handlers to avoid racing
     * the 1s scheduler thread against player HTTP requests on the same Redis session.
     *
     *  1) close rounds whose grace deadline has passed
     *  2) reconcile full rosters → match start pending
     */
    @Scheduled(fixedDelay = 1000)
    public void processAuctioningSessions() {
        Instant now = Instant.now();
        Map<UUID, Card> cardMap = loadCardMap();
        for (ActiveSession snapshot : sessionStore.findByStatus(SessionStatus.AUCTIONING)) {
            UUID sessionId = snapshot.getId();
            try {
                mutationGuard.mutateVoid(sessionId, () -> {
                    ActiveSession session = sessionStore.getById(sessionId);
                    if (session.getStatus() != SessionStatus.AUCTIONING) {
                        return;
                    }
                    Optional<ActiveRound> openOpt = session.findOpenRound();
                    if (openOpt.isPresent()) {
                        ActiveRound round = openOpt.get();
                        Instant graceDeadline = round.getEndsAt().plusMillis(BID_GRACE_PERIOD_MS);
                        if (!graceDeadline.isAfter(now)) {
                            closeRound(round, session, session.getVersion(), cardMap);
                        }
                    }
                    if (session.playerCount() >= 2 && isSessionRostersFull(session)) {
                        enterMatchStartPending(session, session.getVersion());
                    }
                });
            } catch (ActiveSessionStore.OptimisticLockException e) {
                // Another writer modified this session; retry next tick
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT && "Session busy, retry".equals(e.getReason())) {
                    // Lock held by an HTTP mutation; retry next tick
                } else {
                    throw e;
                }
            }
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void expireMatchStartCountdowns() {
        Instant now = Instant.now();
        for (ActiveSession snapshot : sessionStore.findByStatus(SessionStatus.MATCH_START_PENDING)) {
            UUID sessionId = snapshot.getId();
            if (snapshot.getMatchStartDeadlineAt() == null || snapshot.getMatchStartDeadlineAt().isAfter(now)) {
                continue;
            }
            mutationGuard.mutateVoid(sessionId, () -> {
                ActiveSession session = sessionStore.getById(sessionId);
                if (session.getStatus() != SessionStatus.MATCH_START_PENDING) {
                    return;
                }
                if (session.getMatchStartDeadlineAt() == null || session.getMatchStartDeadlineAt().isAfter(now)) {
                    return;
                }
                finalizeSessionFromFullRosters(session, session.getVersion());
            });
        }
    }

    // --- Internal ---

    private void closeRound(ActiveRound round, ActiveSession session, long vBefore, Map<UUID, Card> cardMap) {
        UUID sessionId = session.getId();
        if (round.getStatus() != RoundStatus.OPEN || session.getStatus() != SessionStatus.AUCTIONING) {
            return;
        }
        broadcastRoundResolving(sessionId, round.getId());
        Card roundCard = cardMap.get(round.getCardId());

        if (round.getHighestBidderId() == null) {
            round.markPassed();
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of("round", toResponse(round, roundCard, session, cardMap), "result", "PASSED")
            );
            maybeFinalizeSession(session, cardMap);
            maybeStartNextRound(session);
            meterRegistry.counter("auction.round.closed", "result", "PASSED").increment();
            return;
        }

        ActivePlayer winner = session.getPlayers().stream()
            .filter(p -> p.getId().equals(round.getHighestBidderId()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Winner not found"));

        if (winner.isRosterFull()) {
            round.markPassed();
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of("round", toResponse(round, roundCard, session, cardMap), "result", "PASSED", "reason", "winner_roster_full")
            );
            maybeFinalizeSession(session, cardMap);
            maybeStartNextRound(session);
            meterRegistry.counter("auction.round.closed", "result", "PASSED").increment();
            return;
        }
        if (!RosterSlotAssignment.canFillCard(winner, roundCard, cardMap)) {
            round.markPassed();
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of("round", toResponse(round, roundCard, session, cardMap), "result", "PASSED", "reason", "winner_position_full")
            );
            maybeFinalizeSession(session, cardMap);
            maybeStartNextRound(session);
            meterRegistry.counter("auction.round.closed", "result", "PASSED").increment();
            return;
        }

        awardCardToWinner(winner, roundCard, round.getHighestBidCents(), cardMap);
        round.markSold();
        sessionStore.saveIfVersionMatch(session, vBefore);

        sseEmitterRegistry.broadcast(
            sessionId, "round_ended",
            Map.of("round", toResponse(round, roundCard, session, cardMap), "result", "SOLD", "winnerUserId", winner.getUserId())
        );
        maybeFinalizeSession(session, cardMap);
        maybeStartNextRound(session);
        meterRegistry.counter("auction.round.closed", "result", "SOLD").increment();
    }

    private void maybeStartNextRound(ActiveSession session) {
        if (session.getStatus() != SessionStatus.AUCTIONING) return;
        if (isSessionRostersFull(session)) {
            long vBefore = session.getVersion();
            enterMatchStartPending(session, vBefore);
            return;
        }
        if (session.findOpenRound().isPresent()) return;
        startRoundInternal(session);
    }

    /**
     * Idempotent: opens the first or next round when auction is active and none is open.
     * Called when auction begins and after every round close on the server.
     */
    public void ensureOpenRound(UUID sessionId) {
        mutationGuard.mutateVoid(sessionId, () -> maybeStartNextRound(sessionStore.getById(sessionId)));
    }

    /**
     * Reassigns turn when the current holder cannot bid, or closes the round when neither player can act.
     *
     * @return true when the round was settled in memory (PASSED or SOLD); caller must persist via {@link #finalizeSettledRound}
     */
    private boolean normalizeTurnForRound(ActiveRound round, ActiveSession session, Map<UUID, Card> cardMap) {
        if (round.getStatus() != RoundStatus.OPEN || round.getCurrentTurnUserId() == null) {
            return round.getStatus() != RoundStatus.OPEN;
        }
        Card roundCard = cardMap.get(round.getCardId());
        UUID currentTurnUserId = round.getCurrentTurnUserId();
        ActivePlayer currentPlayer = session.findPlayerByUserId(currentTurnUserId).orElse(null);
        long minimumAllowed = round.getHighestBidCents() == 0 ? STARTING_BID : round.getHighestBidCents() + MIN_INCREMENT;

        boolean currentCanBid = currentPlayer != null
            && !round.hasUserPassed(currentTurnUserId)
            && canPlayerBidRound(currentPlayer, roundCard, minimumAllowed, cardMap);
        if (currentCanBid) return false;

        if (round.getHighestBidderId() != null) {
            settleRoundInline(round, session, cardMap);
            return round.getStatus() != RoundStatus.OPEN;
        }

        UUID otherUserId = otherPlayerUserId(session, currentTurnUserId);
        ActivePlayer otherPlayer = session.findPlayerByUserId(otherUserId).orElse(null);
        if (otherPlayer != null
            && !round.hasUserPassed(otherUserId)
            && canPlayerBidRound(otherPlayer, roundCard, minimumAllowed, cardMap)) {
            round.setCurrentTurnUserId(otherUserId);
            return false;
        }

        round.markPassed();
        return true;
    }

    private void finalizeSettledRound(
        ActiveRound round,
        ActiveSession session,
        long vBefore,
        Map<UUID, Card> cardMap,
        boolean chainNextRound
    ) {
        UUID sessionId = session.getId();
        broadcastRoundResolving(sessionId, round.getId());
        sessionStore.saveIfVersionMatch(session, vBefore);
        broadcastSettledRound(round, session, cardMap);
        if (round.getStatus() == RoundStatus.PASSED) {
            meterRegistry.counter("auction.round.closed", "result", "PASSED").increment();
        }
        maybeFinalizeSession(session, cardMap);
        if (chainNextRound) {
            maybeStartNextRound(session);
        }
    }

    private AuctionRoundResponse resolveMutationResponse(
        ActiveSession session,
        Map<UUID, Card> cardMap,
        UUID userId,
        Card closedRoundCard,
        ActiveRound closedRound
    ) {
        return session.findOpenRound()
            .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
            .orElse(toResponseWithContext(closedRound, closedRoundCard, session, cardMap, userId));
    }

    /** 
     * Settles a round as SOLD inline (called from normalizeTurnForRound when no counter-bid is possible).
     * NOTE: Does NOT broadcast - caller must save session first then call broadcastSettledRound().
     */
    private void settleRoundInline(ActiveRound round, ActiveSession session, Map<UUID, Card> cardMap) {
        Card roundCard = cardMap.get(round.getCardId());
        ActivePlayer winner = session.getPlayers().stream()
            .filter(p -> p.getId().equals(round.getHighestBidderId()))
            .findFirst().orElse(null);

        if (winner == null || winner.isRosterFull()
                || !RosterSlotAssignment.canFillCard(winner, roundCard, cardMap)) {
            round.markPassed();
        } else {
            awardCardToWinner(winner, roundCard, round.getHighestBidCents(), cardMap);
            round.markSold();
        }
    }
    
    /** Broadcasts round_ended event for a settled round. Call AFTER session is saved to Redis. */
    private void broadcastSettledRound(ActiveRound round, ActiveSession session, Map<UUID, Card> cardMap) {
        if (round.getStatus() == RoundStatus.OPEN) return;
        
        Card roundCard = cardMap.get(round.getCardId());
        UUID sessionId = session.getId();
        
        if (round.getStatus() == RoundStatus.PASSED) {
            String reason = round.getHighestBidderId() != null ? "winner_cannot_take" : "both_passed";
            sseEmitterRegistry.broadcast(sessionId, "round_ended",
                Map.of("round", toResponse(round, roundCard, session, cardMap), "result", "PASSED", "reason", reason));
        } else if (round.getStatus() == RoundStatus.SOLD) {
            ActivePlayer winner = session.getPlayers().stream()
                .filter(p -> p.getId().equals(round.getHighestBidderId()))
                .findFirst().orElse(null);
            if (winner != null) {
                sseEmitterRegistry.broadcast(sessionId, "round_ended",
                    Map.of("round", toResponse(round, roundCard, session, cardMap), "result", "SOLD", "winnerUserId", winner.getUserId()));
            }
            meterRegistry.counter("auction.round.closed", "result", "SOLD").increment();
        }
    }

    private void maybeFinalizeSession(ActiveSession session, Map<UUID, Card> cardMap) {
        if (session.getStatus() != SessionStatus.AUCTIONING) return;
        if (session.playerCount() < 2) return;
        if (!isSessionRostersFull(session)) return;
        long vBefore = session.getVersion();
        enterMatchStartPending(session, vBefore);
    }

    private void enterMatchStartPending(ActiveSession session, long vBefore) {
        if (session.getStatus() != SessionStatus.AUCTIONING) return;
        if (session.playerCount() < 2) return;
        if (!isSessionRostersFull(session)) return;
        UUID sessionId = session.getId();

        session.findOpenRound().ifPresent(openRound -> {
            openRound.markPassed();
            Map<UUID, Card> cardMap = loadCardMap();
            Card roundCard = cardMap.get(openRound.getCardId());
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of("round", toResponse(openRound, roundCard, session, cardMap), "result", "PASSED", "reason", "both_rosters_complete")
            );
        });

        session.moveToMatchStartPending(Instant.now().plus(2, ChronoUnit.SECONDS));
        sessionStore.saveIfVersionMatch(session, vBefore);
        sseEmitterRegistry.broadcast(
            sessionId, "match_start_pending",
            Map.of("sessionId", sessionId, "deadlineAt", session.getMatchStartDeadlineAt())
        );
    }

    private void finalizeSessionFromFullRosters(ActiveSession session, long vBefore) {
        if (session.getStatus() != SessionStatus.MATCH_START_PENDING) return;
        if (session.playerCount() < 2) return;
        if (!isSessionRostersFull(session)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot finalize before both rosters are full");
        }
        session.moveToFinalizing(session.getId().getMostSignificantBits());
        sessionStore.saveIfVersionMatch(session, vBefore);

        Map<UUID, Card> cardMap = loadCardMap();
        publishSessionFinalized(session, cardMap);
        sseEmitterRegistry.broadcast(
            session.getId(), "session_finalizing",
            Map.of("sessionId", session.getId(), "reason", "rosters_complete", "seed", session.getMatchSeed())
        );
    }

    private void publishSessionFinalized(ActiveSession session, Map<UUID, Card> cardMap) {
        if (session.playerCount() < 2) return;
        List<ActivePlayer> sorted = session.playersSortedById();
        ActivePlayer home = sorted.get(0);
        ActivePlayer away = sorted.get(1);

        List<SessionFinalizedEvent.RatedCard> homeRoster = toRatedRoster(home, cardMap);
        List<SessionFinalizedEvent.RatedCard> awayRoster = toRatedRoster(away, cardMap);

        session.moveToAwaitingResult();
        sessionStore.save(session);
        publisher.publishEvent(new SessionFinalizedEvent(
            UUID.randomUUID(), Instant.now(), session.getId(),
            session.getMatchSeed() == null ? session.getId().getMostSignificantBits() : session.getMatchSeed(),
            home.getFormation(), away.getFormation(), homeRoster, awayRoster,
            home.getUserId(), away.getUserId()
        ));
    }

    private void broadcastRoundResolving(UUID sessionId, UUID roundId) {
        sseEmitterRegistry.broadcast(
            sessionId, "round_resolving",
            Map.of("sessionId", sessionId, "roundId", roundId)
        );
    }

    // --- Helpers ---

    private UUID resolveHighestBidderUserId(ActiveRound round, ActiveSession session) {
        if (round.getHighestBidderId() == null) return null;
        return session.getPlayers().stream()
            .filter(p -> p.getId().equals(round.getHighestBidderId()))
            .map(ActivePlayer::getUserId)
            .findFirst().orElse(null);
    }

    /**
     * Shared SSE broadcast payload — no viewer-specific flags, but does include
     * highestBidderUserId and opponentCanBid so clients can apply correct UI state
     * without a follow-up REST call.
     */
    private AuctionRoundResponse toResponse(ActiveRound round, Card card, ActiveSession session, Map<UUID, Card> cardMap) {
        long minimumBid = minimumBidForRound(round);
        boolean opponentCanBid = true;
        if (round.getCurrentTurnUserId() != null) {
            UUID opponentUserId = otherPlayerUserId(session, round.getCurrentTurnUserId());
            ActivePlayer opponent = session.findPlayerByUserId(opponentUserId).orElse(null);
            opponentCanBid = opponent != null
                && !round.hasUserPassed(opponentUserId)
                && canPlayerBidRound(opponent, card, minimumBid, cardMap);
        }
        return buildRoundResponse(round, card, session.getId(), session.getVersion(),
            resolveHighestBidderUserId(round, session), opponentCanBid,
            false, false, false, false);
    }

    private AuctionRoundResponse toResponseWithContext(ActiveRound round, Card card,
                                                       ActiveSession session, Map<UUID, Card> cardMap,
                                                       UUID viewerUserId) {
        long minimumBid = minimumBidForRound(round);
        boolean opponentCanBid = true;
        if (round.getCurrentTurnUserId() != null) {
            UUID opponentUserId = otherPlayerUserId(session, round.getCurrentTurnUserId());
            ActivePlayer opponent = session.findPlayerByUserId(opponentUserId).orElse(null);
            opponentCanBid = opponent != null
                && !round.hasUserPassed(opponentUserId)
                && canPlayerBidRound(opponent, card, minimumBid, cardMap);
        }
        boolean viewerCanBid = computeViewerCanBid(round, session, viewerUserId, card, cardMap);
        boolean viewerCanTake = computeViewerCanTake(round, session, viewerUserId, card, cardMap);
        boolean viewerCanPass = computeViewerCanPass(round, viewerUserId);
        boolean viewerCanFillCard = computeViewerCanFillCard(session, viewerUserId, card, cardMap);
        return buildRoundResponse(
            round, card, session.getId(), session.getVersion(), resolveHighestBidderUserId(round, session), opponentCanBid,
            viewerCanBid, viewerCanTake, viewerCanPass, viewerCanFillCard
        );
    }

    private AuctionRoundResponse buildRoundResponse(
        ActiveRound round, Card card, UUID sessionId, long sessionVersion,
        UUID highestBidderUserId, boolean opponentCanBid,
        boolean viewerCanBid, boolean viewerCanTake, boolean viewerCanPass, boolean viewerCanFillCard
    ) {
        PlayerAttributes attrs = card.getAttributes();
        Instant turnOpenAt = computeTurnOpenAt(round);
        return new AuctionRoundResponse(
            round.getId(), sessionId, sessionVersion,
            card.getId(), card.getName(), card.getPosition().name(), eligiblePositionNames(card), card.getRating(),
            attrs.pace(), attrs.shooting(), attrs.passing(), attrs.defending(), attrs.physical(),
            round.getHighestBidCents(), round.getHighestBidderId(), highestBidderUserId,
            round.getCurrentTurnUserId(), round.getReadyUserOneId(), round.getReadyUserTwoId(),
            round.getEndsAt(), turnOpenAt, round.getStatus().name(),
            opponentCanBid, viewerCanBid, viewerCanTake, viewerCanPass, viewerCanFillCard,
            round.getPassedUserIds()
        );
    }

    private boolean computeViewerCanFillCard(ActiveSession session, UUID viewerUserId,
                                             Card card, Map<UUID, Card> cardMap) {
        if (viewerUserId == null) {
            return false;
        }
        ActivePlayer viewer = session.findPlayerByUserId(viewerUserId).orElse(null);
        if (viewer == null || viewer.isRosterFull()) {
            return false;
        }
        return RosterSlotAssignment.canFillCard(viewer, card, cardMap);
    }

    private long minimumBidForRound(ActiveRound round) {
        return round.getHighestBidCents() == 0 ? STARTING_BID : round.getHighestBidCents() + MIN_INCREMENT;
    }

    private boolean viewerMayActOnTurn(ActiveRound round, UUID viewerUserId) {
        if (round.getStatus() != RoundStatus.OPEN) {
            return false;
        }
        if (isTurnOpen(round)) {
            return true;
        }
        if (round.getCurrentTurnUserId() == null) {
            return true;
        }
        return round.getCurrentTurnUserId().equals(viewerUserId);
    }

    private boolean computeViewerCanBid(ActiveRound round, ActiveSession session, UUID viewerUserId,
                                        Card card, Map<UUID, Card> cardMap) {
        if (viewerUserId == null || !viewerMayActOnTurn(round, viewerUserId)) {
            return false;
        }
        if (round.hasUserPassed(viewerUserId)) {
            return false;
        }
        ActivePlayer viewer = session.findPlayerByUserId(viewerUserId).orElse(null);
        if (viewer == null) {
            return false;
        }
        return canPlayerBidRound(viewer, card, minimumBidForRound(round), cardMap);
    }

    private boolean computeViewerCanTake(ActiveRound round, ActiveSession session, UUID viewerUserId,
                                         Card card, Map<UUID, Card> cardMap) {
        if (viewerUserId == null || isTurnOpen(round)) {
            return false;
        }
        if (round.getCurrentTurnUserId() == null || !round.getCurrentTurnUserId().equals(viewerUserId)) {
            return false;
        }
        if (round.hasUserPassed(viewerUserId)) {
            return false;
        }
        ActivePlayer viewer = session.findPlayerByUserId(viewerUserId).orElse(null);
        long minimumBid = minimumBidForRound(round);
        if (viewer == null || !canPlayerBidRound(viewer, card, minimumBid, cardMap)) {
            return false;
        }
        UUID opponentUserId = otherPlayerUserId(session, viewerUserId);
        if (round.hasUserPassed(opponentUserId)) {
            return true;
        }
        ActivePlayer opponent = session.findPlayerByUserId(opponentUserId).orElse(null);
        return opponent == null || !canPlayerBidRound(opponent, card, minimumBid, cardMap);
    }

    private boolean computeViewerCanPass(ActiveRound round, UUID viewerUserId) {
        if (viewerUserId == null || !viewerMayActOnTurn(round, viewerUserId)) {
            return false;
        }
        return !round.hasUserPassed(viewerUserId);
    }

    private List<AuctionTeamCardResponse> buildTeamCards(
        ActivePlayer player, Map<UUID, Card> cardMap,
        Map<UUID, Long> soldAmountByCardId, Map<UUID, Integer> draftOrderByCardId
    ) {
        return player.getCardIds().stream()
            .map(cardId -> {
                Card card = cardMap.get(cardId);
                Position assigned = RosterSlotAssignment.assignedPosition(player, cardId, cardMap);
                return new AuctionTeamCardResponse(
                    card.getId(), card.getName(), assigned.name(), card.getRating(),
                    soldAmountByCardId.getOrDefault(card.getId(), 0L)
                );
            })
            .sorted(Comparator.comparingInt(c -> draftOrderByCardId.getOrDefault(c.cardId(), Integer.MAX_VALUE)))
            .toList();
    }

    private UUID determineOpeningBidder(ActiveSession session, Map<UUID, Card> cardMap) {
        return session.latestRound()
            .map(prev -> {
                if (prev.getStatus() == RoundStatus.SOLD) {
                    ActivePlayer winner = session.getPlayers().stream()
                        .filter(p -> p.getId().equals(prev.getHighestBidderId()))
                        .findFirst().orElse(null);
                    if (winner != null) return otherPlayerUserId(session, winner.getUserId());
                }
                UUID prevOpening = prev.getOpeningBidderUserId();
                if (prevOpening != null) return prevOpening;
                return session.getPlayers().stream().map(ActivePlayer::getUserId).sorted().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Cannot determine opening bidder"));
            })
            .orElseGet(() -> session.getPlayers().stream().map(ActivePlayer::getUserId).sorted().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Cannot determine opening bidder")));
    }

    /**
     * Returns true if the turn holder has been idle for TURN_OPEN_AFTER_SECONDS,
     * meaning bidding is now open to both players.
     */
    private boolean isTurnOpen(ActiveRound round) {
        if (round.getCurrentTurnUserId() == null || round.getTurnStartedAt() == null) return false;
        return Instant.now().isAfter(round.getTurnStartedAt().plusSeconds(TURN_OPEN_AFTER_SECONDS));
    }

    private Instant computeTurnOpenAt(ActiveRound round) {
        if (round.getTurnStartedAt() == null) return null;
        return round.getTurnStartedAt().plusSeconds(TURN_OPEN_AFTER_SECONDS);
    }

    private UUID otherPlayerUserId(ActiveSession session, UUID currentUserId) {
        return session.getPlayers().stream()
            .map(ActivePlayer::getUserId)
            .filter(uid -> !uid.equals(currentUserId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Opponent not found"));
    }

    private boolean canPlayerBidRound(ActivePlayer player, Card roundCard, long amountCents, Map<UUID, Card> cardMap) {
        if (player.isRosterFull()) return false;
        if (!RosterSlotAssignment.canFillCard(player, roundCard, cardMap)) return false;
        if (amountCents > player.getRemainingBudgetCents()) return false;
        int totalOpen = RosterSlotAssignment.totalOpenSlots(player, cardMap);
        long remainingAfterBid = player.getRemainingBudgetCents() - amountCents;
        long reserveRequired = Math.max(totalOpen - 1, 0);
        return remainingAfterBid >= reserveRequired;
    }

    private void awardCardToWinner(ActivePlayer winner, Card roundCard, long amountCents, Map<UUID, Card> cardMap) {
        Position assigned = RosterSlotAssignment.resolveAssignment(winner, roundCard, cardMap)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No open slot for this card"));
        winner.spendBudget(amountCents);
        winner.addCard(roundCard.getId(), assigned);
    }

    private List<SessionFinalizedEvent.RatedCard> toRatedRoster(ActivePlayer player, Map<UUID, Card> cardMap) {
        return player.getCardIds().stream()
            .map(cardId -> {
                Card card = cardMap.get(cardId);
                Position assigned = RosterSlotAssignment.assignedPosition(player, cardId, cardMap);
                return new SessionFinalizedEvent.RatedCard(
                    card.getId(), assigned, card.getRating(), card.getName(), card.getAttributes()
                );
            })
            .toList();
    }

    private List<String> eligiblePositionNames(Card card) {
        return card.orderedEligiblePositionNames();
    }

    private boolean isSessionRostersFull(ActiveSession session) {
        return session.getPlayers().stream().allMatch(ActivePlayer::isRosterFull);
    }

    private UUID parseUuid(String raw, String message) {
        try { return UUID.fromString(raw); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    }
}
