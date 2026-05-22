package dev.sportdraft.bidding.control;

import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.RoundStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RoundCardSelector {

    record SelectionResult(ActiveRound round, int nextPoolIndex) {}

    private RoundCardSelector() {}

    static SelectionResult findNextEligibleRound(ActiveSession session, Map<UUID, Card> cardMap) {
        List<UUID> pool = session.getCardPool();
        Set<UUID> sold = session.soldCardIds();
        int poolSize = pool.size();
        int checked = 0;
        int idx = session.getCardPoolIndex();

        while (checked < poolSize) {
            if (idx >= poolSize) {
                idx = 0;
            }
            UUID cardId = pool.get(idx);
            idx++;
            checked++;

            if (sold.contains(cardId)) {
                continue;
            }

            Card card = cardMap.get(cardId);
            if (card == null) {
                continue;
            }

            boolean anyEligible = session.getPlayers().stream()
                .anyMatch(p -> RosterSlotAssignment.canFillCard(p, card, cardMap));
            if (!anyEligible) {
                continue;
            }

            Instant now = Instant.now();
            ActiveRound round = new ActiveRound(
                UUID.randomUUID(), card.getId(), now,
                now.plus(AuctionTiming.ROUND_DURATION_SECONDS, ChronoUnit.SECONDS),
                determineOpeningBidder(session), RoundStatus.OPEN
            );
            return new SelectionResult(round, idx);
        }
        return null;
    }

    private static UUID determineOpeningBidder(ActiveSession session) {
        session.ensureTurnOrder();
        return session.latestRound()
            .map(prev -> {
                if (prev.getStatus() == RoundStatus.SOLD) {
                    ActivePlayer winner = session.getPlayers().stream()
                        .filter(p -> p.getId().equals(prev.getHighestBidderId()))
                        .findFirst().orElse(null);
                    if (winner != null) {
                        return AuctionRules.nextTurnUserId(session, winner.getUserId());
                    }
                }
                UUID prevOpening = prev.getOpeningBidderUserId();
                if (prevOpening != null) {
                    return AuctionRules.nextTurnUserId(session, prevOpening);
                }
                return session.getTurnOrder().get(0);
            })
            .orElseGet(() -> session.getTurnOrder().get(0));
    }
}
