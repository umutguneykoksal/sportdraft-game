package com.example.auction.bidding.boundary;

import java.util.List;

public record AuctionStateResponse(
    AuctionRoundResponse currentRound,
    List<AuctionTeamCardResponse> myTeam,
    List<AuctionTeamCardResponse> opponentTeam,
    String opponentFormation
) {
}
