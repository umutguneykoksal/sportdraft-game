package com.example.auction.simulation.boundary;

import com.example.auction.bidding.boundary.AuctionTeamCardResponse;
import java.util.List;

public record MatchResultResponse(
    /** Viewer-relative score (legacy field names). */
    int homeScore,
    int awayScore,
    List<AuctionTeamCardResponse> myTeam,
    List<AuctionTeamCardResponse> opponentTeam,
    String myFormation,
    String opponentFormation,
    String myNickname,
    String opponentNickname,
    String myTeamId,
    int myShots,
    int opponentShots,
    int myFouls,
    int opponentFouls,
    String homeUserId,
    String awayUserId,
    String sessionHomeNickname,
    String sessionAwayNickname,
    int sessionHomeScore,
    int sessionAwayScore,
    int sessionHomePenaltyScore,
    int sessionAwayPenaltyScore,
    int myPenaltyScore,
    int opponentPenaltyScore,
    String winnerUserId
) {
}
