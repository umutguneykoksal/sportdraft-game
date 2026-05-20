package com.example.auction.bidding.control;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import java.time.Instant;
import java.util.Map;
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
        return session.getPlayers().stream()
            .map(ActivePlayer::getUserId)
            .filter(uid -> !uid.equals(currentUserId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Opponent not found"));
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
