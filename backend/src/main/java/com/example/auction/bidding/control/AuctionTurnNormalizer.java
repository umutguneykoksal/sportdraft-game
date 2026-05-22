package com.example.auction.bidding.control;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class AuctionTurnNormalizer {

    private final AuctionSettlementService settlement;

    AuctionTurnNormalizer(AuctionSettlementService settlement) {
        this.settlement = settlement;
    }

    /**
     * Skips players who cannot bid, settles when appropriate, or marks the round passed.
     *
     * @return true when the round is no longer open (settled or passed)
     */
    boolean normalizeTurnForRound(ActiveRound round, ActiveSession session, Map<UUID, Card> cardMap) {
        if (round.getStatus() != RoundStatus.OPEN || round.getCurrentTurnUserId() == null) {
            return round.getStatus() != RoundStatus.OPEN;
        }
        Card roundCard = cardMap.get(round.getCardId());
        UUID currentTurnUserId = round.getCurrentTurnUserId();
        ActivePlayer currentPlayer = session.findPlayerByUserId(currentTurnUserId).orElse(null);
        long minimumAllowed = round.getHighestBidCents() == 0
            ? AuctionTiming.STARTING_BID
            : round.getHighestBidCents() + AuctionTiming.MIN_INCREMENT;

        boolean currentCanBid = currentPlayer != null
            && !round.hasUserPassed(currentTurnUserId)
            && AuctionRules.canPlayerBidRound(currentPlayer, roundCard, minimumAllowed, cardMap);
        if (currentCanBid) {
            return false;
        }

        if (round.getHighestBidderId() != null) {
            if (AuctionRules.maySettleLeadingRound(session, round, roundCard, cardMap)) {
                settlement.settleRoundInline(round, session, cardMap);
                return round.getStatus() != RoundStatus.OPEN;
            }
            Optional<UUID> nextBidder = AuctionRules.findNextEligibleBidder(
                session, round, roundCard, cardMap, currentTurnUserId
            );
            if (nextBidder.isPresent()) {
                round.setCurrentTurnUserId(nextBidder.get());
                return false;
            }
            Optional<UUID> nextActor = AuctionRules.findNextNonPassedParticipant(
                session, round, currentTurnUserId, true
            );
            if (nextActor.isPresent()) {
                round.setCurrentTurnUserId(nextActor.get());
                return false;
            }
            if (AuctionRules.maySettleLeadingRound(session, round, roundCard, cardMap)) {
                settlement.settleRoundInline(round, session, cardMap);
            }
            return round.getStatus() != RoundStatus.OPEN;
        }

        Optional<UUID> nextBidder = AuctionRules.findNextEligibleBidder(
            session, round, roundCard, cardMap, currentTurnUserId
        );
        if (nextBidder.isPresent()) {
            round.setCurrentTurnUserId(nextBidder.get());
            return false;
        }

        round.markPassed();
        return true;
    }
}
