package dev.sportdraft.session.boundary;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.boundary.AuctionTeamCardResponse;
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
