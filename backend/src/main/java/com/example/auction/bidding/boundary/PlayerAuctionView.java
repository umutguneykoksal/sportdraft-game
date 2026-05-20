package com.example.auction.bidding.boundary;

import java.util.List;
import java.util.UUID;

public record PlayerAuctionView(
    UUID userId,
    String nickname,
    long budgetRemaining,
    int rosterSize,
    boolean currentTurn,
    String formation,
    List<AuctionTeamCardResponse> team
) {
}
