package com.example.auction.bidding.control;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.AuctionTeamCardResponse;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.PlayerAttributes;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds auction round and team card API responses (package-private). */
final class AuctionRoundResponses {

    private AuctionRoundResponses() {}

    static AuctionRoundResponse toResponse(
        ActiveRound round, Card card, ActiveSession session, Map<UUID, Card> cardMap
    ) {
        long minimumBid = AuctionRules.minimumBidForRound(round);
        boolean opponentCanBid = true;
        opponentCanBid = AuctionRules.anyOtherPlayerCanBid(session, round, card, cardMap, null);
        return buildRoundResponse(
            round, card, session.getId(), session.getVersion(),
            resolveHighestBidderUserId(round, session), opponentCanBid,
            false, false, false, false
        );
    }

    static AuctionRoundResponse toResponseWithContext(
        ActiveRound round, Card card, ActiveSession session, Map<UUID, Card> cardMap, UUID viewerUserId
    ) {
        long minimumBid = AuctionRules.minimumBidForRound(round);
        boolean opponentCanBid = true;
        opponentCanBid = AuctionRules.anyOtherPlayerCanBid(session, round, card, cardMap, viewerUserId);
        boolean viewerCanBid = computeViewerCanBid(round, session, viewerUserId, card, cardMap);
        boolean viewerCanTake = computeViewerCanTake(round, session, viewerUserId, card, cardMap);
        boolean viewerCanPass = computeViewerCanPass(round, viewerUserId);
        boolean viewerCanFillCard = computeViewerCanFillCard(session, viewerUserId, card, cardMap);
        return buildRoundResponse(
            round, card, session.getId(), session.getVersion(),
            resolveHighestBidderUserId(round, session), opponentCanBid,
            viewerCanBid, viewerCanTake, viewerCanPass, viewerCanFillCard
        );
    }

    static AuctionRoundResponse resolveMutationResponse(
        ActiveSession session,
        Map<UUID, Card> cardMap,
        UUID userId,
        Card closedRoundCard,
        ActiveRound closedRound
    ) {
        return session.findOpenRound()
            .map(r -> toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
            .orElse(toResponseWithContext(closedRound, closedRoundCard, session, cardMap, userId));
    }

    static List<AuctionTeamCardResponse> buildTeamCards(
        ActivePlayer player,
        Map<UUID, Card> cardMap,
        Map<UUID, Long> soldAmountByCardId,
        Map<UUID, Integer> draftOrderByCardId
    ) {
        return player.getCardIds().stream()
            .map(cardId -> {
                Card card = cardMap.get(cardId);
                Position assigned = RosterSlotAssignment.assignedPosition(player, cardId, cardMap);
                return new AuctionTeamCardResponse(
                    card.getId(), card.getName(), card.getClubName(), assigned.name(), card.getRating(),
                    soldAmountByCardId.getOrDefault(card.getId(), 0L)
                );
            })
            .sorted(Comparator.comparingInt(c -> draftOrderByCardId.getOrDefault(c.cardId(), Integer.MAX_VALUE)))
            .toList();
    }

    private static AuctionRoundResponse buildRoundResponse(
        ActiveRound round, Card card, UUID sessionId, long sessionVersion,
        UUID highestBidderUserId, boolean opponentCanBid,
        boolean viewerCanBid, boolean viewerCanTake, boolean viewerCanPass, boolean viewerCanFillCard
    ) {
        PlayerAttributes attrs = card.getAttributes();
        Instant turnOpenAt = AuctionRules.computeTurnOpenAt(round);
        return new AuctionRoundResponse(
            round.getId(), sessionId, sessionVersion,
            card.getId(), card.getName(), card.getClubName(), card.getPosition().name(), card.orderedEligiblePositionNames(), card.getRating(),
            attrs.pace(), attrs.shooting(), attrs.passing(), attrs.defending(), attrs.physical(),
            round.getHighestBidCents(), round.getHighestBidderId(), highestBidderUserId,
            round.getCurrentTurnUserId(), round.getReadyUserOneId(), round.getReadyUserTwoId(),
            round.getEndsAt(), turnOpenAt, round.getStatus().name(),
            opponentCanBid, viewerCanBid, viewerCanTake, viewerCanPass, viewerCanFillCard,
            round.getPassedUserIds()
        );
    }

    private static UUID resolveHighestBidderUserId(ActiveRound round, ActiveSession session) {
        if (round.getHighestBidderId() == null) {
            return null;
        }
        return session.getPlayers().stream()
            .filter(p -> p.getId().equals(round.getHighestBidderId()))
            .map(ActivePlayer::getUserId)
            .findFirst().orElse(null);
    }

    private static boolean computeViewerCanFillCard(
        ActiveSession session, UUID viewerUserId, Card card, Map<UUID, Card> cardMap
    ) {
        if (viewerUserId == null) {
            return false;
        }
        ActivePlayer viewer = session.findPlayerByUserId(viewerUserId).orElse(null);
        if (viewer == null || viewer.isRosterFull()) {
            return false;
        }
        return RosterSlotAssignment.canFillCard(viewer, card, cardMap);
    }

    private static boolean computeViewerCanBid(
        ActiveRound round, ActiveSession session, UUID viewerUserId, Card card, Map<UUID, Card> cardMap
    ) {
        if (viewerUserId == null || !AuctionRules.viewerMayActOnTurn(round, viewerUserId)) {
            return false;
        }
        if (round.hasUserPassed(viewerUserId)) {
            return false;
        }
        if (AuctionRules.viewerIsLeadingBidder(session, round, viewerUserId)) {
            return false;
        }
        ActivePlayer viewer = session.findPlayerByUserId(viewerUserId).orElse(null);
        if (viewer == null) {
            return false;
        }
        return AuctionRules.canPlayerBidRound(viewer, card, AuctionRules.minimumBidForRound(round), cardMap);
    }

    private static boolean computeViewerCanTake(
        ActiveRound round, ActiveSession session, UUID viewerUserId, Card card, Map<UUID, Card> cardMap
    ) {
        if (viewerUserId == null || AuctionRules.isTurnOpen(round)) {
            return false;
        }
        if (round.getCurrentTurnUserId() == null || !round.getCurrentTurnUserId().equals(viewerUserId)) {
            return false;
        }
        if (round.hasUserPassed(viewerUserId)) {
            return false;
        }
        ActivePlayer viewer = session.findPlayerByUserId(viewerUserId).orElse(null);
        long minimumBid = AuctionRules.minimumBidForRound(round);
        if (viewer == null || !AuctionRules.canPlayerBidRound(viewer, card, minimumBid, cardMap)) {
            return false;
        }
        return !AuctionRules.anyOtherPlayerCanBid(session, round, card, cardMap, viewerUserId);
    }

    private static boolean computeViewerCanPass(ActiveRound round, UUID viewerUserId) {
        if (viewerUserId == null || !AuctionRules.viewerMayActOnTurn(round, viewerUserId)) {
            return false;
        }
        return !round.hasUserPassed(viewerUserId);
    }
}
