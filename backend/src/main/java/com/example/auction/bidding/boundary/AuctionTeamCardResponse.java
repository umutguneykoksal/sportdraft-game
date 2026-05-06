package com.example.auction.bidding.boundary;

import java.util.UUID;

public record AuctionTeamCardResponse(
    UUID cardId,
    String cardName,
    String position,
    long amountCents
) {
}
