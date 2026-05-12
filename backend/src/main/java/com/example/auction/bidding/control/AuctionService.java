package com.example.auction.bidding.control;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.AuctionStateResponse;
import com.example.auction.bidding.boundary.AuctionTeamCardResponse;
import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.CardRepository;
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
import java.util.function.Function;
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
    private static final long MAX_ANTI_SNIPE_SECONDS = 30L;
    private static final long ANTI_SNIPE_EXTENSION_SECONDS = 5L;

    private final ActiveSessionStore sessionStore;
    private final CardRepository cardRepository;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ApplicationEventPublisher publisher;
    private final MeterRegistry meterRegistry;

    public AuctionService(
        ActiveSessionStore sessionStore,
        CardRepository cardRepository,
        SseEmitterRegistry sseEmitterRegistry,
        ApplicationEventPublisher publisher,
        MeterRegistry meterRegistry
    ) {
        this.sessionStore = sessionStore;
        this.cardRepository = cardRepository;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
    }

    // --- Card lookup (master data from PostgreSQL, cached per call) ---

    private Map<UUID, Card> loadCardMap() {
        return cardRepository.findAll().stream()
            .collect(Collectors.toMap(Card::getId, Function.identity()));
    }

    private Card getCard(UUID cardId) {
        return cardRepository.findById(cardId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found: " + cardId));
    }

    // --- Public API ---

    public void confirmMatchStart(UUID sessionId, UUID userId) {
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
        ActiveSession session = sessionStore.getById(sessionId);
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
        List<Card> available = new java.util.ArrayList<>(
            cardMap.values().stream()
                .filter(c -> !session.usedCardIds().contains(c.getId()))
                .toList()
        );
        if (available.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No cards available");
        }
        Collections.shuffle(available);

        ActiveRound createdRound = null;
        for (Card card : available) {
            boolean anyEligible = session.getPlayers().stream()
                .anyMatch(p -> canBidForPosition(p, card.getPosition(), cardMap));
            Instant now = Instant.now();
            if (!anyEligible) {
                ActiveRound skipped = new ActiveRound(
                    UUID.randomUUID(), card.getId(), now, now, null, RoundStatus.SKIPPED
                );
                session.getRounds().add(skipped);
                sseEmitterRegistry.broadcast(
                    sessionId, "round_skipped",
                    Map.of("round", toResponse(skipped, card, sessionId), "reason", "no_open_position_slots")
                );
                continue;
            }
            UUID openingBidder = determineOpeningBidder(session, cardMap);
            ActiveRound round = new ActiveRound(
                UUID.randomUUID(), card.getId(), now, now.plus(15, ChronoUnit.SECONDS),
                openingBidder, RoundStatus.OPEN
            );
            session.getRounds().add(round);
            normalizeTurnForRound(round, session, cardMap);
            if (round.getStatus() == RoundStatus.PASSED) {
                continue;
            }
            createdRound = round;
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
        AuctionRoundResponse response = toResponseWithContext(createdRound, roundCard, session, cardMap);
        sseEmitterRegistry.broadcast(sessionId, "round_started", Map.of("round", response));
        meterRegistry.counter("auction.round.started").increment();
        return response;
    }

    public AuctionRoundResponse placeBid(UUID sessionId, UUID userId, String idempotencyKey, PlaceBidRequest request) {
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
        if (Instant.now().isAfter(round.getEndsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round has already ended");
        }

        UUID idem = parseUuid(idempotencyKey, "Invalid Idempotency-Key value");
        Card roundCard = cardMap.get(round.getCardId());
        if (round.hasBidWithIdempotencyKey(idem)) {
            return toResponse(round, roundCard, sessionId);
        }

        ActivePlayer bidder = session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));
        if (bidder.isRosterFull()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Roster is full");
        }
        normalizeTurnForRound(round, session, cardMap);
        if (round.getStatus() != RoundStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open");
        }
        if (round.getCurrentTurnUserId() != null && !round.getCurrentTurnUserId().equals(userId)) {
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
        if (openSlotsForPosition(bidder, roundCard.getPosition(), cardMap) <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No open slot for this card position");
        }
        int totalOpen = totalOpenSlots(bidder, cardMap);
        long remainingAfterBid = bidder.getRemainingBudgetCents() - request.amountCents();
        long reserveRequired = Math.max(totalOpen - 1, 0);
        if (remainingAfterBid < reserveRequired) {
            long maxAllowed = bidder.getRemainingBudgetCents() - reserveRequired;
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Must reserve %d cr for %d remaining slots. Maximum bid: %d cr"
                    .formatted(reserveRequired, Math.max(totalOpen - 1, 0), maxAllowed));
        }

        Instant newEndsAt = round.getEndsAt();
        if (Instant.now().isAfter(round.getEndsAt().minus(ANTI_SNIPE_EXTENSION_SECONDS, ChronoUnit.SECONDS))) {
            Instant capped = round.getStartsAt().plus(15 + MAX_ANTI_SNIPE_SECONDS, ChronoUnit.SECONDS);
            Instant proposed = round.getEndsAt().plus(ANTI_SNIPE_EXTENSION_SECONDS, ChronoUnit.SECONDS);
            newEndsAt = proposed.isAfter(capped) ? capped : proposed;
        }

        UUID nextTurn = otherPlayerUserId(session, userId);
        ActiveBid bid = new ActiveBid(UUID.randomUUID(), bidder.getId(), request.amountCents(), idem, Instant.now());
        round.getBids().add(bid);
        round.registerBid(bidder.getId(), request.amountCents(), newEndsAt, nextTurn);
        normalizeTurnForRound(round, session, cardMap);
        sessionStore.saveIfVersionMatch(session, vBefore);

        AuctionRoundResponse response = toResponseWithContext(round, roundCard, session, cardMap);
        sseEmitterRegistry.broadcast(
            sessionId, "bid_placed",
            Map.of("round", response, "userId", userId, "amountCents", request.amountCents())
        );
        meterRegistry.counter("auction.bid.placed").increment();
        return response;
    }

    public AuctionRoundResponse readyNext(UUID sessionId, UUID userId, UUID roundId) {
        ActiveSession session = sessionStore.getById(sessionId);
        long vBefore = session.getVersion();
        Map<UUID, Card> cardMap = loadCardMap();

        session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));

        ActiveRound round = session.findRoundById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (round.getStatus() != RoundStatus.OPEN) {
            return session.findOpenRound()
                .map(r -> toResponse(r, cardMap.get(r.getCardId()), sessionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open"));
        }
        if (round.getHighestBidderId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ready-next is only allowed when there is no bid on the round");
        }

        round.registerReady(userId);
        if (round.areBothReady()) {
            closeRound(round, session, vBefore, cardMap);
            return session.findOpenRound()
                .map(r -> toResponse(r, cardMap.get(r.getCardId()), sessionId))
                .orElse(toResponse(round, cardMap.get(round.getCardId()), sessionId));
        } else {
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "round_ready",
                Map.of("round", toResponse(round, cardMap.get(round.getCardId()), sessionId), "userId", userId)
            );
            return toResponse(round, cardMap.get(round.getCardId()), sessionId);
        }
    }

    public AuctionRoundResponse passRound(UUID sessionId, UUID userId, UUID roundId) {
        ActiveSession session = sessionStore.getById(sessionId);
        long vBefore = session.getVersion();
        Map<UUID, Card> cardMap = loadCardMap();

        session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));

        ActiveRound round = session.findRoundById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (round.getStatus() != RoundStatus.OPEN) {
            return session.findOpenRound()
                .map(r -> toResponse(r, cardMap.get(r.getCardId()), sessionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open"));
        }
        if (round.getCurrentTurnUserId() != null && !round.getCurrentTurnUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not your turn");
        }

        round.registerPass(userId);
        Card roundCard = cardMap.get(round.getCardId());

        UUID opponentUserId = otherPlayerUserId(session, userId);
        ActivePlayer opponent = session.findPlayerByUserId(opponentUserId).orElse(null);
        long minimumBid = round.getHighestBidCents() == 0 ? STARTING_BID : round.getHighestBidCents() + MIN_INCREMENT;
        boolean opponentCanBid = opponent != null && canPlayerBidRound(opponent, roundCard, minimumBid, cardMap);

        if (opponentCanBid && !round.hasUserPassed(opponentUserId)) {
            round.setCurrentTurnUserId(opponentUserId);
            sessionStore.saveIfVersionMatch(session, vBefore);
            AuctionRoundResponse response = toResponseWithContext(round, roundCard, session, cardMap);
            sseEmitterRegistry.broadcast(sessionId, "turn_passed",
                Map.of("round", response, "userId", userId));
            return response;
        }

        closeRound(round, session, vBefore, cardMap);
        return session.findOpenRound()
            .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap))
            .orElse(toResponse(round, cardMap.get(round.getCardId()), sessionId));
    }

    public AuctionStateResponse getAuctionState(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.getById(sessionId);
        TeamSnapshot teams = buildTeamSnapshot(session, userId);

        Map<UUID, Card> cardMap = loadCardMap();
        AuctionRoundResponse currentRound = session.findOpenRound()
            .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap))
            .orElse(null);

        return new AuctionStateResponse(currentRound, teams.myTeam(), teams.opponentTeam(), teams.myFormation(), teams.opponentFormation());
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

    // --- Scheduled jobs ---

    @Scheduled(fixedDelay = 1000)
    public void closeExpiredRounds() {
        Instant now = Instant.now();
        Map<UUID, Card> cardMap = null;
        for (ActiveSession session : sessionStore.findByStatus(SessionStatus.AUCTIONING)) {
            Optional<ActiveRound> openOpt = session.findOpenRound();
            if (openOpt.isEmpty()) continue;
            ActiveRound round = openOpt.get();
            if (round.getEndsAt().isAfter(now)) continue;
            if (cardMap == null) cardMap = loadCardMap();
            long vBefore = session.getVersion();
            closeRound(round, session, vBefore, cardMap);
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void normalizeOpenRounds() {
        Map<UUID, Card> cardMap = null;
        for (ActiveSession session : sessionStore.findByStatus(SessionStatus.AUCTIONING)) {
            Optional<ActiveRound> openOpt = session.findOpenRound();
            if (openOpt.isEmpty()) continue;
            if (cardMap == null) cardMap = loadCardMap();
            long vBefore = session.getVersion();
            normalizeTurnForRound(openOpt.get(), session, cardMap);
            if (session.getVersion() == vBefore) {
                // normalizeTurn may have changed state, save if needed
                // Only save if round was mutated (status changed to PASSED)
                if (openOpt.get().getStatus() != RoundStatus.OPEN) {
                    sessionStore.saveIfVersionMatch(session, vBefore);
                }
            }
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void reconcileRostersCompleteAuctions() {
        for (ActiveSession session : sessionStore.findByStatus(SessionStatus.AUCTIONING)) {
            if (session.playerCount() < 2) continue;
            if (isSessionRostersFull(session)) {
                long vBefore = session.getVersion();
                enterMatchStartPending(session, vBefore);
            }
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void expireMatchStartCountdowns() {
        Instant now = Instant.now();
        for (ActiveSession session : sessionStore.findByStatus(SessionStatus.MATCH_START_PENDING)) {
            if (session.getMatchStartDeadlineAt() != null && !session.getMatchStartDeadlineAt().isAfter(now)) {
                long vBefore = session.getVersion();
                finalizeSessionFromFullRosters(session, vBefore);
            }
        }
    }

    // --- Internal ---

    private void closeRound(ActiveRound round, ActiveSession session, long vBefore, Map<UUID, Card> cardMap) {
        UUID sessionId = session.getId();
        if (round.getStatus() != RoundStatus.OPEN || session.getStatus() != SessionStatus.AUCTIONING) {
            return;
        }
        Card roundCard = cardMap.get(round.getCardId());

        if (round.getHighestBidderId() == null) {
            round.markPassed();
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of("round", toResponse(round, roundCard, sessionId), "result", "PASSED")
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
                Map.of("round", toResponse(round, roundCard, sessionId), "result", "PASSED", "reason", "winner_roster_full")
            );
            maybeFinalizeSession(session, cardMap);
            maybeStartNextRound(session);
            meterRegistry.counter("auction.round.closed", "result", "PASSED").increment();
            return;
        }
        if (openSlotsForPosition(winner, roundCard.getPosition(), cardMap) <= 0) {
            round.markPassed();
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of("round", toResponse(round, roundCard, sessionId), "result", "PASSED", "reason", "winner_position_full")
            );
            maybeFinalizeSession(session, cardMap);
            maybeStartNextRound(session);
            meterRegistry.counter("auction.round.closed", "result", "PASSED").increment();
            return;
        }

        winner.spendBudget(round.getHighestBidCents());
        winner.addCard(roundCard.getId());
        round.markSold();
        sessionStore.saveIfVersionMatch(session, vBefore);

        sseEmitterRegistry.broadcast(
            sessionId, "round_ended",
            Map.of("round", toResponse(round, roundCard, sessionId), "result", "SOLD", "winnerUserId", winner.getUserId())
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
        startRound(session.getId());
    }

    private void normalizeTurnForRound(ActiveRound round, ActiveSession session, Map<UUID, Card> cardMap) {
        if (round.getStatus() != RoundStatus.OPEN || round.getCurrentTurnUserId() == null) return;
        Card roundCard = cardMap.get(round.getCardId());
        UUID currentTurnUserId = round.getCurrentTurnUserId();
        ActivePlayer currentPlayer = session.findPlayerByUserId(currentTurnUserId).orElse(null);
        long minimumAllowed = round.getHighestBidCents() == 0 ? STARTING_BID : round.getHighestBidCents() + MIN_INCREMENT;

        if (currentPlayer != null && canPlayerBidRound(currentPlayer, roundCard, minimumAllowed, cardMap)) return;

        UUID otherUserId = otherPlayerUserId(session, currentTurnUserId);
        ActivePlayer otherPlayer = session.findPlayerByUserId(otherUserId).orElse(null);
        if (otherPlayer != null && canPlayerBidRound(otherPlayer, roundCard, minimumAllowed, cardMap)) {
            round.setCurrentTurnUserId(otherUserId);
            return;
        }

        round.markPassed();
        sseEmitterRegistry.broadcast(
            session.getId(), "round_ended",
            Map.of("round", toResponse(round, roundCard, session.getId()), "result", "PASSED", "reason", "no_eligible_turn_bidder")
        );
        maybeFinalizeSession(session, cardMap);
        maybeStartNextRound(session);
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
                Map.of("round", toResponse(openRound, roundCard, sessionId), "result", "PASSED", "reason", "both_rosters_complete")
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

        List<SessionFinalizedEvent.RatedCard> homeRoster = home.getCardIds().stream()
            .map(cardMap::get)
            .map(c -> new SessionFinalizedEvent.RatedCard(c.getId(), c.getPosition(), c.getRating()))
            .toList();
        List<SessionFinalizedEvent.RatedCard> awayRoster = away.getCardIds().stream()
            .map(cardMap::get)
            .map(c -> new SessionFinalizedEvent.RatedCard(c.getId(), c.getPosition(), c.getRating()))
            .toList();

        session.moveToAwaitingResult();
        sessionStore.save(session);
        publisher.publishEvent(new SessionFinalizedEvent(
            UUID.randomUUID(), Instant.now(), session.getId(),
            session.getMatchSeed() == null ? session.getId().getMostSignificantBits() : session.getMatchSeed(),
            home.getFormation(), away.getFormation(), homeRoster, awayRoster
        ));
    }

    // --- Helpers ---

    private AuctionRoundResponse toResponse(ActiveRound round, Card card, UUID sessionId) {
        return new AuctionRoundResponse(
            round.getId(), sessionId,
            card.getId(), card.getName(), card.getPosition().name(), card.getRating(),
            round.getHighestBidCents(), round.getHighestBidderId(),
            round.getCurrentTurnUserId(), round.getReadyUserOneId(), round.getReadyUserTwoId(),
            round.getEndsAt(), round.getStatus().name(),
            true
        );
    }

    private AuctionRoundResponse toResponseWithContext(ActiveRound round, Card card,
                                                       ActiveSession session, Map<UUID, Card> cardMap) {
        boolean opponentCanBid = true;
        if (round.getCurrentTurnUserId() != null) {
            UUID opponentUserId = otherPlayerUserId(session, round.getCurrentTurnUserId());
            ActivePlayer opponent = session.findPlayerByUserId(opponentUserId).orElse(null);
            long minimumBid = round.getHighestBidCents() == 0 ? STARTING_BID : round.getHighestBidCents() + MIN_INCREMENT;
            opponentCanBid = opponent != null && canPlayerBidRound(opponent, card, minimumBid, cardMap);
        }
        return new AuctionRoundResponse(
            round.getId(), session.getId(),
            card.getId(), card.getName(), card.getPosition().name(), card.getRating(),
            round.getHighestBidCents(), round.getHighestBidderId(),
            round.getCurrentTurnUserId(), round.getReadyUserOneId(), round.getReadyUserTwoId(),
            round.getEndsAt(), round.getStatus().name(),
            opponentCanBid
        );
    }

    private List<AuctionTeamCardResponse> buildTeamCards(
        ActivePlayer player, Map<UUID, Card> cardMap,
        Map<UUID, Long> soldAmountByCardId, Map<UUID, Integer> draftOrderByCardId
    ) {
        return player.getCardIds().stream()
            .map(cardMap::get)
            .map(card -> new AuctionTeamCardResponse(
                card.getId(), card.getName(), card.getPosition().name(), card.getRating(),
                soldAmountByCardId.getOrDefault(card.getId(), 0L)
            ))
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

    private UUID otherPlayerUserId(ActiveSession session, UUID currentUserId) {
        return session.getPlayers().stream()
            .map(ActivePlayer::getUserId)
            .filter(uid -> !uid.equals(currentUserId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Opponent not found"));
    }

    private boolean canBidForPosition(ActivePlayer player, Position position, Map<UUID, Card> cardMap) {
        if (player.isRosterFull()) return false;
        return openSlotsForPosition(player, position, cardMap) > 0 && player.getRemainingBudgetCents() >= STARTING_BID;
    }

    private boolean canPlayerBidRound(ActivePlayer player, Card roundCard, long amountCents, Map<UUID, Card> cardMap) {
        if (player.isRosterFull()) return false;
        if (openSlotsForPosition(player, roundCard.getPosition(), cardMap) <= 0) return false;
        if (amountCents > player.getRemainingBudgetCents()) return false;
        int totalOpen = totalOpenSlots(player, cardMap);
        long remainingAfterBid = player.getRemainingBudgetCents() - amountCents;
        long reserveRequired = Math.max(totalOpen - 1, 0);
        return remainingAfterBid >= reserveRequired;
    }

    private int openSlotsForPosition(ActivePlayer player, Position position, Map<UUID, Card> cardMap) {
        int required = player.getFormation().slotCount(position);
        long filled = player.getCardIds().stream()
            .map(cardMap::get)
            .filter(c -> c.getPosition() == position)
            .count();
        return Math.max(required - (int) filled, 0);
    }

    private int totalOpenSlots(ActivePlayer player, Map<UUID, Card> cardMap) {
        int total = 0;
        for (Position pos : Position.values()) {
            total += openSlotsForPosition(player, pos, cardMap);
        }
        return total;
    }

    private boolean isSessionRostersFull(ActiveSession session) {
        return session.getPlayers().stream().allMatch(ActivePlayer::isRosterFull);
    }

    private UUID parseUuid(String raw, String message) {
        try { return UUID.fromString(raw); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    }
}
