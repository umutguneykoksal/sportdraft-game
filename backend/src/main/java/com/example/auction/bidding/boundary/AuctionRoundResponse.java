package com.example.auction.bidding.boundary;

import java.time.Instant;
import java.util.UUID;

public record AuctionRoundResponse(
    UUID roundId,
    UUID sessionId,
    UUID cardId,
    String cardName,
    String position,
    long highestBidCents,
    UUID highestBidderId,
    UUID currentTurnUserId,
    UUID readyUserOneId,
    UUID readyUserTwoId,
    Instant endsAt,
    String status
) {
}
