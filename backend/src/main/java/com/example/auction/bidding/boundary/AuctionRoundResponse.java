package com.example.auction.bidding.boundary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuctionRoundResponse(
    UUID roundId,
    UUID sessionId,
    UUID cardId,
    String cardName,
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
    boolean opponentCanBid
) {
}
