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
    Integer homePenaltyScore,
    Integer awayPenaltyScore,
    String status,
    boolean playable,
    boolean watchedByMe,
    boolean replayAllowed,
    boolean allPlayersWatched,
    long watchCount,
    int requiredWatchers
) {
}
