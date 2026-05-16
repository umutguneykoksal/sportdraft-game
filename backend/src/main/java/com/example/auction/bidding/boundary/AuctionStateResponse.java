package com.example.auction.bidding.boundary;

import java.util.List;

public record AuctionStateResponse(
    AuctionRoundResponse currentRound,
    List<AuctionTeamCardResponse> myTeam,
    List<AuctionTeamCardResponse> opponentTeam,
    String myFormation,
    String opponentFormation,
    /** Session version at the time this state was read; clients use to detect stale data. */
    long version
) {
}
