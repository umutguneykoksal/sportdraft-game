package com.example.auction.simulation.boundary;

import com.example.auction.bidding.boundary.AuctionTeamCardResponse;
import java.util.List;

public record MatchResultResponse(
    int homeScore,
    int awayScore,
    List<AuctionTeamCardResponse> myTeam,
    List<AuctionTeamCardResponse> opponentTeam,
    String myFormation,
    String opponentFormation,
    String myNickname,
    String opponentNickname
) {
}
