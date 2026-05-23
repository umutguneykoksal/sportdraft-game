package dev.sportdraft.bidding.control;

import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.bidding.entity.RoundStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared auction business rules (package-private). */
final class AuctionRules {

    private AuctionRules() {}

    static boolean isSessionRostersFull(ActiveSession session) {
        return session.getPlayers().stream().allMatch(ActivePlayer::isRosterFull);
    }

    static UUID otherPlayerUserId(ActiveSession session, UUID currentUserId) {
        if (session.getMaxPlayers() <= 2) {
            return session.getPlayers().stream()
                .map(ActivePlayer::getUserId)
                .filter(uid -> !uid.equals(currentUserId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Opponent not found"));
        }
        return nextTurnUserId(session, currentUserId);
    }

    static UUID nextTurnUserId(ActiveSession session, UUID currentUserId) {
        session.ensureTurnOrder();
        List<UUID> order = session.getTurnOrder();
        if (order.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Turn order not established");
        }
        int start = order.indexOf(currentUserId);
        if (start < 0) {
            start = 0;
        }
        for (int step = 1; step <= order.size(); step++) {
            UUID next = order.get((start + step) % order.size());
            if (!next.equals(currentUserId)) {
                return next;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "No next player in turn order");
    }

    static Optional<UUID> findNextEligibleBidder(
        ActiveSession session,
        ActiveRound round,
        Card roundCard,
        Map<UUID, Card> cardMap,
        UUID afterUserId
    ) {
        session.ensureTurnOrder();
        List<UUID> order = session.getTurnOrder();
        int start = afterUserId != null ? order.indexOf(afterUserId) : -1;
        if (start < 0) {
            start = 0;
        }
        long minimum = minimumBidForRound(round);
        for (int step = 1; step <= order.size(); step++) {
            UUID candidate = order.get((start + step) % order.size());
            if (round.hasUserPassed(candidate)) {
                continue;
            }
            ActivePlayer player = session.findPlayerByUserId(candidate).orElse(null);
            if (player == null) {
                continue;
            }
            if (round.getHighestBidderId() != null && player.getId().equals(round.getHighestBidderId())) {
                continue;
            }
            if (canPlayerBidRound(player, roundCard, minimum, cardMap)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Next player who has not passed (may need to pass even if they cannot raise the bid). */
    static Optional<UUID> findNextNonPassedParticipant(
        ActiveSession session,
        ActiveRound round,
        UUID afterUserId,
        boolean skipLeadingBidder
    ) {
        session.ensureTurnOrder();
        List<UUID> order = session.getTurnOrder();
        UUID winnerUserId = null;
        if (skipLeadingBidder && round.getHighestBidderId() != null) {
            winnerUserId = session.getPlayers().stream()
                .filter(p -> p.getId().equals(round.getHighestBidderId()))
                .map(ActivePlayer::getUserId)
                .findFirst()
                .orElse(null);
        }
        int start = afterUserId != null ? order.indexOf(afterUserId) : -1;
        if (start < 0) {
            start = 0;
        }
        for (int step = 1; step <= order.size(); step++) {
            UUID candidate = order.get((start + step) % order.size());
            if (round.hasUserPassed(candidate)) {
                continue;
            }
            if (winnerUserId != null && winnerUserId.equals(candidate)) {
                continue;
            }
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    static boolean anyOtherPlayerCanBid(
        ActiveSession session,
        ActiveRound round,
        Card card,
        Map<UUID, Card> cardMap,
        UUID viewerUserId
    ) {
        long minimum = minimumBidForRound(round);
        return session.getPlayers().stream()
            .map(ActivePlayer::getUserId)
            .filter(uid -> viewerUserId == null || !uid.equals(viewerUserId))
            .anyMatch(uid -> !round.hasUserPassed(uid)
                && session.findPlayerByUserId(uid)
                    .map(p -> canPlayerBidRound(p, card, minimum, cardMap))
                    .orElse(false));
    }

    static boolean hasFullLobby(ActiveSession session) {
        return session.playerCount() >= session.getMaxPlayers();
    }

    /** 2p: one opponent pass ends the round; 4p+: every non-winner must pass unless no one can counter-bid. */
    static boolean maySettleLeadingRound(
        ActiveSession session,
        ActiveRound round,
        Card card,
        Map<UUID, Card> cardMap
    ) {
        if (round.getHighestBidderId() == null) {
            return false;
        }
        if (session.getMaxPlayers() <= 2) {
            return true;
        }
        UUID winnerUserId = session.getPlayers().stream()
            .filter(p -> p.getId().equals(round.getHighestBidderId()))
            .map(ActivePlayer::getUserId)
            .findFirst()
            .orElse(null);
        if (winnerUserId == null) {
            return false;
        }
        if (card != null && !anyOtherPlayerCanBid(session, round, card, cardMap, winnerUserId)) {
            return true;
        }
        return allNonWinnersHavePassed(session, round);
    }

    /** After a bid, round ends only when every non-winning player has passed (4p+). */
    static boolean allNonWinnersHavePassed(ActiveSession session, ActiveRound round) {
        if (round.getHighestBidderId() == null) {
            return false;
        }
        UUID winnerUserId = session.getPlayers().stream()
            .filter(p -> p.getId().equals(round.getHighestBidderId()))
            .map(ActivePlayer::getUserId)
            .findFirst()
            .orElse(null);
        if (winnerUserId == null) {
            return false;
        }
        return session.getPlayers().stream()
            .map(ActivePlayer::getUserId)
            .filter(uid -> !uid.equals(winnerUserId))
            .allMatch(round::hasUserPassed);
    }

    static long minimumBidForRound(ActiveRound round) {
        return round.getHighestBidCents() == 0
            ? AuctionTiming.STARTING_BID
            : round.getHighestBidCents() + AuctionTiming.MIN_INCREMENT;
    }

    /**
     * Exclusive turn ends after {@link AuctionTiming#TURN_OPEN_AFTER_SECONDS}, or earlier when the
     * round clock has at most that many seconds left (so late bids cannot reset the window forever).
     */
    static Instant biddingOpensAt(ActiveRound round) {
        if (round.getStartsAt() == null) {
            return null;
        }
        return round.getStartsAt().plusSeconds(AuctionTiming.ROUND_REVEAL_SECONDS);
    }

    static boolean isBiddingOpen(ActiveRound round) {
        Instant opensAt = biddingOpensAt(round);
        return opensAt == null || !Instant.now().isBefore(opensAt);
    }

    static boolean isTurnOpen(ActiveRound round) {
        if (!isBiddingOpen(round)) {
            return false;
        }
        Instant now = Instant.now();
        Instant openAt = computeTurnOpenAt(round);
        return openAt != null && !now.isBefore(openAt);
    }

    static Instant computeTurnOpenAt(ActiveRound round) {
        Instant effectiveTurnStart = effectiveTurnStartedAt(round);
        Instant fromTurn = effectiveTurnStart != null
            ? effectiveTurnStart.plusSeconds(AuctionTiming.TURN_OPEN_AFTER_SECONDS)
            : null;
        Instant fromRoundEnd = round.getEndsAt() != null
            ? round.getEndsAt().minusSeconds(AuctionTiming.TURN_OPEN_AFTER_SECONDS)
            : null;
        if (fromTurn == null) {
            return fromRoundEnd;
        }
        if (fromRoundEnd == null) {
            return fromTurn;
        }
        return fromTurn.isBefore(fromRoundEnd) ? fromTurn : fromRoundEnd;
    }

    /** Turn clock runs from bidding open; opening turn is stored at round {@code startsAt}. */
    static Instant effectiveTurnStartedAt(ActiveRound round) {
        Instant turnStartedAt = round.getTurnStartedAt();
        if (turnStartedAt == null) {
            return null;
        }
        Instant biddingOpensAt = biddingOpensAt(round);
        if (biddingOpensAt != null && turnStartedAt.isBefore(biddingOpensAt)) {
            return biddingOpensAt;
        }
        return turnStartedAt;
    }

    static boolean canPlayerBidRound(ActivePlayer player, Card roundCard, long amountCents, Map<UUID, Card> cardMap) {
        if (player.isRosterFull()) {
            return false;
        }
        if (!RosterSlotAssignment.canFillCard(player, roundCard, cardMap)) {
            return false;
        }
        if (amountCents > player.getRemainingBudgetCents()) {
            return false;
        }
        int totalOpen = RosterSlotAssignment.totalOpenSlots(player, cardMap);
        long remainingAfterBid = player.getRemainingBudgetCents() - amountCents;
        long reserveRequired = Math.max(totalOpen - 1, 0);
        return remainingAfterBid >= reserveRequired;
    }

    static void awardCardToWinner(ActivePlayer winner, Card roundCard, long amountCents, Map<UUID, Card> cardMap) {
        Position assigned = RosterSlotAssignment.resolveAssignment(winner, roundCard, cardMap)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No open slot for this card"));
        winner.spendBudget(amountCents);
        winner.addCard(roundCard.getId(), assigned);
    }

    static UUID parseUuid(String raw, String message) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    static boolean viewerIsLeadingBidder(ActiveSession session, ActiveRound round, UUID viewerUserId) {
        if (viewerUserId == null || round.getHighestBidderId() == null || round.getHighestBidCents() <= 0) {
            return false;
        }
        return session.findPlayerByUserId(viewerUserId)
            .map(p -> p.getId().equals(round.getHighestBidderId()))
            .orElse(false);
    }

    static boolean viewerMayActOnTurn(ActiveRound round, UUID viewerUserId) {
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
}
