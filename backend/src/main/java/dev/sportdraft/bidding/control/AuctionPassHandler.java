package dev.sportdraft.bidding.control;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AuctionPassHandler {

    private final ActiveSessionStore sessionStore;
    private final AuctionSettlementService settlement;

    AuctionPassHandler(ActiveSessionStore sessionStore, AuctionSettlementService settlement) {
        this.sessionStore = sessionStore;
        this.settlement = settlement;
    }

    PassRoundResult pass(
        ActiveSession session,
        long versionBefore,
        UUID userId,
        ActiveRound round,
        Map<UUID, Card> cardMap
    ) {
        Card roundCard = cardMap.get(round.getCardId());

        boolean callerIsHighestBidder = round.getHighestBidderId() != null
            && session.findPlayerByUserId(userId)
                .map(p -> p.getId().equals(round.getHighestBidderId())).orElse(false);

        if (callerIsHighestBidder) {
            Optional<UUID> nextBidder = AuctionRules.findNextEligibleBidder(session, round, roundCard, cardMap, userId);
            if (nextBidder.isEmpty()) {
                settlement.closeRound(round, session, versionBefore, cardMap);
                AuctionRoundResponse response = resolveMutationResponse(session, cardMap, userId, roundCard, round);
                return new PassRoundResult(response, false);
            }
            round.setCurrentTurnUserId(nextBidder.get());
            sessionStore.saveIfVersionMatch(session, versionBefore);
            return new PassRoundResult(
                AuctionRoundResponses.toResponseWithContext(round, roundCard, session, cardMap, userId),
                true
            );
        }

        round.registerPass(userId);

        if (round.getHighestBidderId() != null) {
            if (AuctionRules.maySettleLeadingRound(session, round, roundCard, cardMap)) {
                settlement.closeRound(round, session, versionBefore, cardMap);
                AuctionRoundResponse response = resolveMutationResponse(session, cardMap, userId, roundCard, round);
                return new PassRoundResult(response, false);
            }
        }

        Optional<UUID> nextBidder = AuctionRules.findNextEligibleBidder(session, round, roundCard, cardMap, userId);
        if (nextBidder.isPresent()) {
            round.setCurrentTurnUserId(nextBidder.get());
            sessionStore.saveIfVersionMatch(session, versionBefore);
            return new PassRoundResult(
                AuctionRoundResponses.toResponseWithContext(round, roundCard, session, cardMap, userId),
                true
            );
        }

        settlement.closeRound(round, session, versionBefore, cardMap);
        AuctionRoundResponse response = resolveMutationResponse(session, cardMap, userId, roundCard, round);
        return new PassRoundResult(response, false);
    }

    private AuctionRoundResponse resolveMutationResponse(
        ActiveSession session,
        Map<UUID, Card> cardMap,
        UUID userId,
        Card roundCard,
        ActiveRound round
    ) {
        return session.findOpenRound()
            .map(r -> AuctionRoundResponses.toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
            .orElse(AuctionRoundResponses.toResponse(round, roundCard, session, cardMap));
    }

    record PassRoundResult(AuctionRoundResponse response, boolean broadcastTurnPassed) {}
}
