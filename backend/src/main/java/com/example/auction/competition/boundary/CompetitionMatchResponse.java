package com.example.auction.competition.boundary;

import java.util.UUID;

public record CompetitionMatchResponse(
    UUID matchId,
    String roundKey,
    UUID homeUserId,
    String homeNickname,
    UUID awayUserId,
    String awayNickname,
    Integer homeScore,
    Integer awayScore,
    String status
) {
}
