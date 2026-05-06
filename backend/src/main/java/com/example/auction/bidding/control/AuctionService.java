package com.example.auction.bidding.control;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.AuctionStateResponse;
import com.example.auction.bidding.boundary.AuctionTeamCardResponse;
import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.entity.AuctionRound;
import com.example.auction.bidding.entity.AuctionRoundRepository;
import com.example.auction.bidding.entity.Bid;
import com.example.auction.bidding.entity.BidRepository;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.CardRepository;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameSession;
import com.example.auction.session.entity.GameSessionRepository;
import com.example.auction.session.entity.SessionPlayer;
import com.example.auction.session.entity.SessionPlayerRepository;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.simulation.control.SessionFinalizedEvent;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuctionService {

    private static final long MIN_INCREMENT = 1L;
    private static final long STARTING_BID = 1L;
    private static final int ROSTER_SIZE = 11;
    private static final long MATCH_START_COUNTDOWN_SECONDS = 20L;
    private static final long MAX_ANTI_SNIPE_SECONDS = 30L;
    private static final long ANTI_SNIPE_EXTENSION_SECONDS = 5L;

    private final GameSessionRepository gameSessionRepository;
    private final SessionPlayerRepository sessionPlayerRepository;
    private final AuctionRoundRepository auctionRoundRepository;
    private final CardRepository cardRepository;
    private final BidRepository bidRepository;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ApplicationEventPublisher publisher;

    public AuctionService(
        GameSessionRepository gameSessionRepository,
        SessionPlayerRepository sessionPlayerRepository,
        AuctionRoundRepository auctionRoundRepository,
        CardRepository cardRepository,
        BidRepository bidRepository,
        SseEmitterRegistry sseEmitterRegistry,
        ApplicationEventPublisher publisher
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.sessionPlayerRepository = sessionPlayerRepository;
        this.auctionRoundRepository = auctionRoundRepository;
        this.cardRepository = cardRepository;
        this.bidRepository = bidRepository;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.publisher = publisher;
    }

    public void confirmMatchStart(UUID sessionId, UUID userId) {
        gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        sessionPlayerRepository.findBySession_IdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not in session"));

        GameSession session = gameSessionRepository.lockById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (session.getStatus() != SessionStatus.MATCH_START_PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match start is not pending");
        }
        session.registerMatchStartReady(userId);
        if (session.areBothMatchStartReady()) {
            finalizeSessionFromFullRostersWithSession(session);
        } else {
            sseEmitterRegistry.broadcast(
                sessionId,
                "match_start_ready",
                Map.of(
                    "sessionId", sessionId,
                    "readyCount", session.matchStartReadyCount(),
                    "userId", userId
                )
            );
        }
    }

    public AuctionRoundResponse startRound(UUID sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (session.getStatus() != SessionStatus.AUCTIONING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not auctioning");
        }

        auctionRoundRepository.findFirstBySession_IdAndStatusOrderByStartsAtDesc(sessionId, RoundStatus.OPEN)
            .ifPresent(round -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An active round already exists");
            });

        List<SessionPlayer> players = sessionPlayerRepository.findBySession_Id(sessionId);
        if (players.size() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot start auction round without two players");
        }

        List<Card> cards = cardRepository.findAll();
        if (cards.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No cards available");
        }
        Collections.shuffle(cards);

        AuctionRound round = null;
        for (Card card : cards) {
            boolean anyEligible = players.stream().anyMatch(player -> canBidForPosition(player, card.getPosition()));
            Instant now = Instant.now();
            if (!anyEligible) {
                AuctionRound skipped = new AuctionRound(
                    UUID.randomUUID(),
                    session,
                    card,
                    now,
                    now,
                    null,
                    RoundStatus.SKIPPED
                );
                auctionRoundRepository.save(skipped);
                sseEmitterRegistry.broadcast(
                    sessionId,
                    "round_skipped",
                    Map.of("round", toResponse(skipped), "reason", "no_open_position_slots")
                );
                continue;
            }
            UUID openingBidderUserId = determineOpeningBidderUserId(sessionId, players);
            round = new AuctionRound(
                UUID.randomUUID(),
                session,
                card,
                now,
                now.plus(15, ChronoUnit.SECONDS),
                openingBidderUserId,
                RoundStatus.OPEN
            );
            auctionRoundRepository.save(round);
            normalizeTurnForRound(round, sessionId);
            if (round.getStatus() == RoundStatus.PASSED) {
                continue;
            }
            break;
        }

        if (round == null) {
            session.moveToFinalizing(session.getId().getMostSignificantBits());
            publishSessionFinalized(session, players);
            sseEmitterRegistry.broadcast(
                sessionId,
                "session_finalizing",
                Map.of("sessionId", sessionId, "reason", "no_eligible_cards")
            );
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No eligible cards remain, session finalizing");
        }

        AuctionRoundResponse response = toResponse(round);
        sseEmitterRegistry.broadcast(
            sessionId,
            "round_started",
            Map.of("round", response)
        );
        return response;
    }

    public AuctionRoundResponse placeBid(UUID sessionId, UUID userId, String idempotencyKey, PlaceBidRequest request) {
        AuctionRound round = auctionRoundRepository.findById(request.roundId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (!round.getSession().getId().equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round does not belong to session");
        }
        if (round.getStatus() != RoundStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open");
        }
        if (Instant.now().isAfter(round.getEndsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round has already ended");
        }

        UUID idem = parseUuid(idempotencyKey, "Invalid Idempotency-Key value");
        if (bidRepository.existsByRoundIdAndIdempotencyKey(round.getId(), idem)) {
            return toResponse(round);
        }

        SessionPlayer bidder = sessionPlayerRepository.findBySession_IdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));
        normalizeTurnForRound(round, sessionId);
        if (round.getStatus() != RoundStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open");
        }
        if (round.getCurrentTurnUserId() != null && !round.getCurrentTurnUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not your turn to bid");
        }

        long minimumAllowed = round.getHighestBidCents() == 0
            ? STARTING_BID
            : round.getHighestBidCents() + MIN_INCREMENT;
        if (request.amountCents() < minimumAllowed) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Bid is below minimum increment");
        }
        if (request.amountCents() > bidder.getRemainingBudgetCents()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient budget");
        }
        int openSlotsForCardPosition = openSlotsForPosition(bidder, round.getCard().getPosition());
        if (openSlotsForCardPosition <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No open slot for this card position");
        }
        int totalOpenSlots = totalOpenSlots(bidder);
        long remainingAfterBid = bidder.getRemainingBudgetCents() - request.amountCents();
        long reserveRequired = Math.max(totalOpenSlots - 1, 0);
        if (remainingAfterBid < reserveRequired) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Bid violates reserve rule: keep at least %d cents for %d remaining slots"
                    .formatted(reserveRequired, Math.max(totalOpenSlots - 1, 0))
            );
        }

        Instant newEndsAt = round.getEndsAt();
        if (Instant.now().isAfter(round.getEndsAt().minus(ANTI_SNIPE_EXTENSION_SECONDS, ChronoUnit.SECONDS))) {
            Instant capped = round.getStartsAt().plus(15 + MAX_ANTI_SNIPE_SECONDS, ChronoUnit.SECONDS);
            Instant proposed = round.getEndsAt().plus(ANTI_SNIPE_EXTENSION_SECONDS, ChronoUnit.SECONDS);
            newEndsAt = proposed.isAfter(capped) ? capped : proposed;
        }

        UUID nextTurnUserId = otherPlayerUserId(sessionId, userId);
        Bid bid = new Bid(UUID.randomUUID(), round, bidder, request.amountCents(), idem, Instant.now());
        bidRepository.save(bid);
        round.registerBid(bidder.getId(), request.amountCents(), newEndsAt, nextTurnUserId);
        normalizeTurnForRound(round, sessionId);

        AuctionRoundResponse response = toResponse(round);
        sseEmitterRegistry.broadcast(
            sessionId,
            "bid_placed",
            Map.of("round", response, "userId", userId, "amountCents", request.amountCents())
        );
        return response;
    }

    public AuctionRoundResponse readyNext(UUID sessionId, UUID userId, UUID roundId) {
        AuctionRound round = auctionRoundRepository.findById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));
        if (!round.getSession().getId().equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round does not belong to session");
        }
        sessionPlayerRepository.findBySession_IdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));
        if (round.getStatus() != RoundStatus.OPEN) {
            return auctionRoundRepository
                .findFirstBySession_IdAndStatusOrderByStartsAtDesc(sessionId, RoundStatus.OPEN)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open"));
        }
        if (round.getHighestBidderId() != null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "ready-next is only allowed when there is no bid on the round"
            );
        }

        round.registerReady(userId);
        if (round.areBothReady()) {
            closeRound(round);
            return auctionRoundRepository
                .findFirstBySession_IdAndStatusOrderByStartsAtDesc(sessionId, RoundStatus.OPEN)
                .map(this::toResponse)
                .orElse(toResponse(round));
        } else {
            sseEmitterRegistry.broadcast(
                sessionId,
                "round_ready",
                Map.of("round", toResponse(round), "userId", userId)
            );
            return toResponse(round);
        }
    }

    public AuctionRoundResponse passRound(UUID sessionId, UUID userId, UUID roundId) {
        AuctionRound round = auctionRoundRepository.findById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));
        if (!round.getSession().getId().equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Round does not belong to session");
        }
        sessionPlayerRepository.findBySession_IdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));
        if (round.getStatus() != RoundStatus.OPEN) {
            return auctionRoundRepository
                .findFirstBySession_IdAndStatusOrderByStartsAtDesc(sessionId, RoundStatus.OPEN)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Round is not open"));
        }
        if (round.getCurrentTurnUserId() != null && !round.getCurrentTurnUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not your turn");
        }

        closeRound(round);
        return auctionRoundRepository
            .findFirstBySession_IdAndStatusOrderByStartsAtDesc(sessionId, RoundStatus.OPEN)
            .map(this::toResponse)
            .orElse(toResponse(round));
    }

    public AuctionStateResponse getAuctionState(UUID sessionId, UUID userId) {
        List<SessionPlayer> players = sessionPlayerRepository.findBySession_Id(sessionId);
        if (players.size() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session does not have two players");
        }
        SessionPlayer me = players.stream()
            .filter(player -> player.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session"));
        SessionPlayer opponent = players.stream()
            .filter(player -> !player.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Opponent not found in session"));

        List<AuctionRound> soldRounds = auctionRoundRepository.findBySession_IdAndStatusOrderByStartsAtAsc(sessionId, RoundStatus.SOLD);
        List<AuctionTeamCardResponse> myTeam = new ArrayList<>();
        List<AuctionTeamCardResponse> opponentTeam = new ArrayList<>();
        for (AuctionRound soldRound : soldRounds) {
            AuctionTeamCardResponse card = new AuctionTeamCardResponse(
                soldRound.getCard().getId(),
                soldRound.getCard().getName(),
                soldRound.getCard().getPosition().name(),
                soldRound.getHighestBidCents()
            );
            if (me.getId().equals(soldRound.getHighestBidderId())) {
                myTeam.add(card);
            } else if (opponent.getId().equals(soldRound.getHighestBidderId())) {
                opponentTeam.add(card);
            }
        }

        AuctionRoundResponse currentRound = auctionRoundRepository
            .findFirstBySession_IdAndStatusOrderByStartsAtDesc(sessionId, RoundStatus.OPEN)
            .map(this::toResponse)
            .orElse(null);

        return new AuctionStateResponse(currentRound, myTeam, opponentTeam);
    }

    @Scheduled(fixedDelay = 1000)
    public void closeExpiredRounds() {
        List<AuctionRound> expired = auctionRoundRepository.findByStatusAndEndsAtLessThanEqual(RoundStatus.OPEN, Instant.now());
        for (AuctionRound round : expired) {
            closeRound(round);
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void expireMatchStartCountdowns() {
        List<GameSession> due = gameSessionRepository.findByStatusAndMatchStartDeadlineAtLessThanEqual(
            SessionStatus.MATCH_START_PENDING,
            Instant.now()
        );
        for (GameSession pending : due) {
            GameSession locked = gameSessionRepository.lockById(pending.getId()).orElse(null);
            if (locked != null && locked.getStatus() == SessionStatus.MATCH_START_PENDING) {
                finalizeSessionFromFullRostersWithSession(locked);
            }
        }
    }

    private void closeRound(AuctionRound round) {
        AuctionRound locked = auctionRoundRepository.lockById(round.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Round not found"));
        if (locked.getStatus() != RoundStatus.OPEN) {
            return;
        }
        if (locked.getSession().getStatus() != SessionStatus.AUCTIONING) {
            return;
        }

        UUID sessionId = locked.getSession().getId();

        if (locked.getHighestBidderId() == null) {
            locked.markPassed();
            sseEmitterRegistry.broadcast(
                sessionId,
                "round_ended",
                Map.of("round", toResponse(locked), "result", "PASSED")
            );
            maybeFinalizeSession(sessionId);
            maybeStartNextRound(sessionId);
            return;
        }

        SessionPlayer winner = sessionPlayerRepository.lockById(locked.getHighestBidderId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Winner not found"));
        if (sessionPlayerRepository.countCardsByPlayerId(winner.getId()) >= ROSTER_SIZE) {
            locked.markPassed();
            sseEmitterRegistry.broadcast(
                sessionId,
                "round_ended",
                Map.of("round", toResponse(locked), "result", "PASSED", "reason", "winner_roster_full")
            );
            maybeFinalizeSession(sessionId);
            maybeStartNextRound(sessionId);
            return;
        }
        if (openSlotsForPosition(winner, locked.getCard().getPosition()) <= 0) {
            locked.markPassed();
            sseEmitterRegistry.broadcast(
                sessionId,
                "round_ended",
                Map.of("round", toResponse(locked), "result", "PASSED", "reason", "winner_position_full")
            );
            maybeFinalizeSession(sessionId);
            maybeStartNextRound(sessionId);
            return;
        }
        winner.spendBudget(locked.getHighestBidCents());
        winner.addCard(locked.getCard());
        locked.markSold();
        sessionPlayerRepository.flush();
        auctionRoundRepository.flush();

        sseEmitterRegistry.broadcast(
            sessionId,
            "round_ended",
            Map.of("round", toResponse(locked), "result", "SOLD", "winnerUserId", winner.getUserId())
        );
        maybeFinalizeSession(sessionId);
        maybeStartNextRound(sessionId);
    }

    private void maybeStartNextRound(UUID sessionId) {
        auctionRoundRepository.flush();
        GameSession session = gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (session.getStatus() != SessionStatus.AUCTIONING) {
            return;
        }
        boolean hasOpenRound = auctionRoundRepository
            .findFirstBySession_IdAndStatusOrderByStartsAtDesc(sessionId, RoundStatus.OPEN)
            .isPresent();
        if (hasOpenRound) {
            return;
        }
        startRound(sessionId);
    }

    private void normalizeTurnForRound(AuctionRound round, UUID sessionId) {
        if (round.getStatus() != RoundStatus.OPEN || round.getCurrentTurnUserId() == null) {
            return;
        }
        UUID currentTurnUserId = round.getCurrentTurnUserId();
        SessionPlayer currentPlayer = sessionPlayerRepository.findBySession_IdAndUserId(sessionId, currentTurnUserId).orElse(null);
        long minimumAllowed = round.getHighestBidCents() == 0
            ? STARTING_BID
            : round.getHighestBidCents() + MIN_INCREMENT;

        if (currentPlayer != null && canPlayerBidRound(currentPlayer, round, minimumAllowed)) {
            return;
        }

        UUID otherUserId = otherPlayerUserId(sessionId, currentTurnUserId);
        SessionPlayer otherPlayer = sessionPlayerRepository.findBySession_IdAndUserId(sessionId, otherUserId).orElse(null);
        if (otherPlayer != null && canPlayerBidRound(otherPlayer, round, minimumAllowed)) {
            round.setCurrentTurnUserId(otherUserId);
            return;
        }

        round.markPassed();
        sseEmitterRegistry.broadcast(
            sessionId,
            "round_ended",
            Map.of("round", toResponse(round), "result", "PASSED", "reason", "no_eligible_turn_bidder")
        );
        maybeFinalizeSession(sessionId);
        maybeStartNextRound(sessionId);
    }

    private AuctionRoundResponse toResponse(AuctionRound round) {
        return new AuctionRoundResponse(
            round.getId(),
            round.getSession().getId(),
            round.getCard().getId(),
            round.getCard().getName(),
            round.getCard().getPosition().name(),
            round.getHighestBidCents(),
            round.getHighestBidderId(),
            round.getCurrentTurnUserId(),
            round.getReadyUserOneId(),
            round.getReadyUserTwoId(),
            round.getEndsAt(),
            round.getStatus().name()
        );
    }

    private UUID determineOpeningBidderUserId(UUID sessionId, List<SessionPlayer> players) {
        return auctionRoundRepository.findFirstBySession_IdOrderByStartsAtDesc(sessionId)
            .map(previousRound -> {
                if (previousRound.getStatus() == RoundStatus.SOLD) {
                    SessionPlayer winner = sessionPlayerRepository.findById(previousRound.getHighestBidderId()).orElse(null);
                    if (winner != null) {
                        return otherPlayerUserId(sessionId, winner.getUserId());
                    }
                }
                UUID previousOpening = previousRound.getOpeningBidderUserId();
                if (previousOpening != null) {
                    return previousOpening;
                }
                return players.stream()
                    .map(SessionPlayer::getUserId)
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Cannot determine opening bidder"));
            })
            .orElseGet(() -> players.stream()
                .map(SessionPlayer::getUserId)
                .sorted()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Cannot determine opening bidder")));
    }

    private UUID otherPlayerUserId(UUID sessionId, UUID currentUserId) {
        return sessionPlayerRepository.findBySession_Id(sessionId).stream()
            .map(SessionPlayer::getUserId)
            .filter(userId -> !userId.equals(currentUserId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Opponent not found"));
    }

    private UUID parseUuid(String raw, String message) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private boolean canBidForPosition(SessionPlayer player, Position position) {
        return openSlotsForPosition(player, position) > 0 && player.getRemainingBudgetCents() >= STARTING_BID;
    }

    private boolean canPlayerBidRound(SessionPlayer player, AuctionRound round, long amountCents) {
        if (openSlotsForPosition(player, round.getCard().getPosition()) <= 0) {
            return false;
        }
        if (amountCents > player.getRemainingBudgetCents()) {
            return false;
        }
        int totalOpenSlots = totalOpenSlots(player);
        long remainingAfterBid = player.getRemainingBudgetCents() - amountCents;
        long reserveRequired = Math.max(totalOpenSlots - 1, 0);
        return remainingAfterBid >= reserveRequired;
    }

    private int openSlotsForPosition(SessionPlayer player, Position position) {
        int required = formationSlotCount(player.getFormation(), position);
        int filled = (int) sessionPlayerRepository.countCardsByPlayerIdAndPosition(player.getId(), position.name());
        return Math.max(required - filled, 0);
    }

    private int totalOpenSlots(SessionPlayer player) {
        return openSlotsForPosition(player, Position.GK)
            + openSlotsForPosition(player, Position.DEF)
            + openSlotsForPosition(player, Position.MID)
            + openSlotsForPosition(player, Position.FWD);
    }

    private int formationSlotCount(Formation formation, Position position) {
        Map<Position, Integer> slots = new EnumMap<>(Position.class);
        switch (formation) {
            case FOUR_FOUR_TWO -> {
                slots.put(Position.GK, 1);
                slots.put(Position.DEF, 4);
                slots.put(Position.MID, 4);
                slots.put(Position.FWD, 2);
            }
            case FOUR_THREE_THREE -> {
                slots.put(Position.GK, 1);
                slots.put(Position.DEF, 4);
                slots.put(Position.MID, 3);
                slots.put(Position.FWD, 3);
            }
            case THREE_FIVE_TWO -> {
                slots.put(Position.GK, 1);
                slots.put(Position.DEF, 3);
                slots.put(Position.MID, 5);
                slots.put(Position.FWD, 2);
            }
        }
        return slots.getOrDefault(position, 0);
    }

    private void maybeFinalizeSession(UUID sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (session.getStatus() != SessionStatus.AUCTIONING) {
            return;
        }
        List<SessionPlayer> players = sessionPlayerRepository.findBySession_Id(sessionId);
        if (players.size() < 2) {
            return;
        }

        boolean allRostersFull = players.stream()
            .allMatch(player -> sessionPlayerRepository.countCardsByPlayerId(player.getId()) >= ROSTER_SIZE);
        boolean everyoneBlocked = players.stream().allMatch(player -> {
            int openSlots = totalOpenSlots(player);
            if (openSlots <= 0) {
                return true;
            }
            long reserveRequiredAfterMinimumBid = Math.max(openSlots - 1, 0);
            return player.getRemainingBudgetCents() < STARTING_BID
                || (player.getRemainingBudgetCents() - STARTING_BID) < reserveRequiredAfterMinimumBid;
        });
        if (!allRostersFull && !everyoneBlocked) {
            return;
        }

        if (allRostersFull) {
            enterMatchStartPending(sessionId);
            return;
        }

        session.moveToFinalizing(session.getId().getMostSignificantBits());
        publishSessionFinalized(session, players);
        sseEmitterRegistry.broadcast(
            sessionId,
            "session_finalizing",
            Map.of(
                "sessionId", sessionId,
                "reason", "players_blocked",
                "seed", session.getMatchSeed()
            )
        );
    }

    private void enterMatchStartPending(UUID sessionId) {
        GameSession session = gameSessionRepository.lockById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (session.getStatus() != SessionStatus.AUCTIONING) {
            return;
        }
        List<SessionPlayer> players = sessionPlayerRepository.findBySession_Id(sessionId);
        if (players.size() < 2) {
            return;
        }
        boolean allFull = players.stream()
            .allMatch(player -> sessionPlayerRepository.countCardsByPlayerId(player.getId()) >= ROSTER_SIZE);
        if (!allFull) {
            return;
        }
        session.moveToMatchStartPending(Instant.now().plus(MATCH_START_COUNTDOWN_SECONDS, ChronoUnit.SECONDS));
        gameSessionRepository.save(session);
        sseEmitterRegistry.broadcast(
            sessionId,
            "match_start_pending",
            Map.of(
                "sessionId", sessionId,
                "deadlineAt", session.getMatchStartDeadlineAt()
            )
        );
    }

    private void finalizeSessionFromFullRostersWithSession(GameSession session) {
        if (session.getStatus() != SessionStatus.MATCH_START_PENDING) {
            return;
        }
        UUID sessionId = session.getId();
        List<SessionPlayer> players = sessionPlayerRepository.findBySession_Id(sessionId);
        session.moveToFinalizing(session.getId().getMostSignificantBits());
        publishSessionFinalized(session, players);
        sseEmitterRegistry.broadcast(
            sessionId,
            "session_finalizing",
            Map.of(
                "sessionId", sessionId,
                "reason", "rosters_complete",
                "seed", session.getMatchSeed()
            )
        );
    }

    private void publishSessionFinalized(GameSession session, List<SessionPlayer> players) {
        if (players.size() < 2) {
            return;
        }
        SessionPlayer home = players.get(0);
        SessionPlayer away = players.get(1);
        List<SessionFinalizedEvent.RatedCard> homeRoster = home.getCards().stream()
            .map(card -> new SessionFinalizedEvent.RatedCard(card.getId(), card.getPosition(), card.getRating()))
            .toList();
        List<SessionFinalizedEvent.RatedCard> awayRoster = away.getCards().stream()
            .map(card -> new SessionFinalizedEvent.RatedCard(card.getId(), card.getPosition(), card.getRating()))
            .toList();

        session.moveToAwaitingResult();
        publisher.publishEvent(
            new SessionFinalizedEvent(
                UUID.randomUUID(),
                Instant.now(),
                session.getId(),
                session.getMatchSeed() == null ? session.getId().getMostSignificantBits() : session.getMatchSeed(),
                home.getFormation(),
                away.getFormation(),
                homeRoster,
                awayRoster
            )
        );
    }
}
