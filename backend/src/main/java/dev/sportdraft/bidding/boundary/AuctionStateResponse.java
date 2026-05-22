package dev.sportdraft.bidding.boundary;

import java.util.List;

public record AuctionStateResponse(
    AuctionRoundResponse currentRound,
    List<AuctionTeamCardResponse> myTeam,
    List<AuctionTeamCardResponse> opponentTeam,
    String myFormation,
    String opponentFormation,
    long version,
    List<PlayerAuctionView> players
) {
}
