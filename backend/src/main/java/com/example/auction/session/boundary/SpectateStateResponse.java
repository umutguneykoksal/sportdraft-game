package com.example.auction.session.boundary;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.AuctionTeamCardResponse;
import java.util.List;
import java.util.UUID;

public record SpectateStateResponse(
    String status,
    AuctionRoundResponse currentRound,
    List<AuctionTeamCardResponse> homeTeam,
    List<AuctionTeamCardResponse> awayTeam,
    String homeFormation,
    String awayFormation,
    UUID homeUserId,
    UUID awayUserId,
    String homeNickname,
    String awayNickname,
    long homeBudget,
    long awayBudget,
    int homeScore,
    int awayScore
) {}
