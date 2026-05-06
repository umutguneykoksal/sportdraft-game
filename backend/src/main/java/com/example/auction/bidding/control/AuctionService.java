package com.example.auction.bidding.control;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
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

    @Scheduled(fixedDelay = 1000)
    public void closeExpiredRounds() {
        List<AuctionRound> expired = auctionRoundRepository.findByStatusAndEndsAtLessThanEqual(RoundStatus.OPEN, Instant.now());
        for (AuctionRound round : expired) {
            closeRound(round);
        }
    }

    private void closeRound(AuctionRound round) {
        if (round.getHighestBidderId() == null) {
            round.markPassed();
            sseEmitterRegistry.broadcast(
                round.getSession().getId(),
                "round_ended",
                Map.of("round", toResponse(round), "result", "PASSED")
            );
            maybeFinalizeSession(round.getSession().getId());
            maybeStartNextRound(round.getSession().getId());
            return;
        }

        SessionPlayer winner = sessionPlayerRepository.findById(round.getHighestBidderId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Winner not found"));
        if (winner.getCards().size() >= ROSTER_SIZE) {
            round.markPassed();
            sseEmitterRegistry.broadcast(
                round.getSession().getId(),
                "round_ended",
                Map.of("round", toResponse(round), "result", "PASSED", "reason", "winner_roster_full")
            );
            maybeFinalizeSession(round.getSession().getId());
            maybeStartNextRound(round.getSession().getId());
            return;
        }
        if (openSlotsForPosition(winner, round.getCard().getPosition()) <= 0) {
            round.markPassed();
            sseEmitterRegistry.broadcast(
                round.getSession().getId(),
                "round_ended",
                Map.of("round", toResponse(round), "result", "PASSED", "reason", "winner_position_full")
            );
            maybeFinalizeSession(round.getSession().getId());
            maybeStartNextRound(round.getSession().getId());
            return;
        }
        winner.spendBudget(round.getHighestBidCents());
        winner.addCard(round.getCard());
        round.markSold();

        sseEmitterRegistry.broadcast(
            round.getSession().getId(),
            "round_ended",
            Map.of("round", toResponse(round), "result", "SOLD", "winnerUserId", winner.getUserId())
        );
        maybeFinalizeSession(round.getSession().getId());
        maybeStartNextRound(round.getSession().getId());
    }

    private void maybeStartNextRound(UUID sessionId) {
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
        int filled = (int) player.getCards().stream()
            .filter(card -> card.getPosition() == position)
            .count();
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

        boolean allRostersFull = players.stream().allMatch(player -> player.getCards().size() >= ROSTER_SIZE);
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

        session.moveToFinalizing(session.getId().getMostSignificantBits());
        publishSessionFinalized(session, players);
        sseEmitterRegistry.broadcast(
            sessionId,
            "session_finalizing",
            Map.of(
                "sessionId", sessionId,
                "reason", allRostersFull ? "rosters_complete" : "players_blocked",
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
