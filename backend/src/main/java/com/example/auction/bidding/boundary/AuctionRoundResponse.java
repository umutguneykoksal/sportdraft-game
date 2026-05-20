package com.example.auction.bidding.boundary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuctionRoundResponse(
    UUID roundId,
    UUID sessionId,
    /** Optimistic-lock version of the session at response time; clients use this to ignore stale WS patches. */
    long version,
    UUID cardId,
    String cardName,
    String cardClubName,
    String position,
    List<String> eligiblePositions,
    int cardRating,
    int pace,
    int shooting,
    int passing,
    int defending,
    int physical,
    long highestBidCents,
    UUID highestBidderId,
    UUID highestBidderUserId,
    UUID currentTurnUserId,
    UUID readyUserOneId,
    UUID readyUserTwoId,
    Instant endsAt,
    Instant turnOpenAt,
    String status,
    boolean opponentCanBid,
    /** Whether the requesting player may place a bid on this round right now. */
    boolean viewerCanBid,
    /** Whether the requesting player may take the card at minimum bid (solo eligible turn). */
    boolean viewerCanTake,
    /** Whether the requesting player may pass / end turn on this round. */
    boolean viewerCanPass,
    /** Whether the card fits an open slot on the viewer's formation (ignores whose turn it is). */
    boolean viewerCanFillCard,
    /** Player UUIDs who have permanently passed on this round (shared; used by clients to derive viewer flags without a REST call). */
    List<UUID> passedPlayerIds
) {
}
