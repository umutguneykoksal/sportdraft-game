package com.example.auction.bidding.control;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
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
            if (player != null && canPlayerBidRound(player, roundCard, minimum, cardMap)) {
                return Optional.of(candidate);
            }
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

    static long minimumBidForRound(ActiveRound round) {
        return round.getHighestBidCents() == 0
            ? AuctionTiming.STARTING_BID
            : round.getHighestBidCents() + AuctionTiming.MIN_INCREMENT;
    }

    static boolean isTurnOpen(ActiveRound round) {
        if (round.getCurrentTurnUserId() == null || round.getTurnStartedAt() == null) {
            return false;
        }
        return Instant.now().isAfter(round.getTurnStartedAt().plusSeconds(AuctionTiming.TURN_OPEN_AFTER_SECONDS));
    }

    static Instant computeTurnOpenAt(ActiveRound round) {
        if (round.getTurnStartedAt() == null) {
            return null;
        }
        return round.getTurnStartedAt().plusSeconds(AuctionTiming.TURN_OPEN_AFTER_SECONDS);
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
