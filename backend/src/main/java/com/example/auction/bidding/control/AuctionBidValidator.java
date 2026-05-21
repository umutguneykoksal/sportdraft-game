package com.example.auction.bidding.control;

import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.entity.Card;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class AuctionBidValidator {

    private AuctionBidValidator() {}

    static void validateBid(
        ActiveSession session,
        ActiveRound round,
        ActivePlayer bidder,
        Card roundCard,
        PlaceBidRequest request,
        Map<UUID, Card> cardMap,
        UUID userId
    ) {
        if (bidder.isRosterFull()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Roster is full");
        }
        boolean turnOpen = AuctionRules.isTurnOpen(round);
        if (!turnOpen && round.getCurrentTurnUserId() != null && !round.getCurrentTurnUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not your turn to bid");
        }
        if (AuctionRules.viewerIsLeadingBidder(session, round, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already hold the highest bid; wait for others");
        }

        long minimumAllowed = round.getHighestBidCents() == 0
            ? AuctionTiming.STARTING_BID
            : round.getHighestBidCents() + AuctionTiming.MIN_INCREMENT;
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
    }
}
