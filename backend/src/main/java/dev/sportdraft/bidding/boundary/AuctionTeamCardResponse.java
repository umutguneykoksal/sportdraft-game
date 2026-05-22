package dev.sportdraft.bidding.boundary;

import java.util.UUID;

public record AuctionTeamCardResponse(
    UUID cardId,
    String cardName,
    String clubName,
    String position,
    int rating,
    long amountCents
) {
}
