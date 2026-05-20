package com.example.auction.competition.boundary;

import java.util.List;
import java.util.UUID;

public record CompetitionResponse(
    UUID competitionId,
    UUID sessionId,
    String type,
    String status,
    String tournamentPhase,
    String intermissionEndsAt,
    boolean replaysUnlocked,
    UUID championUserId,
    String championNickname,
    List<CompetitionMatchResponse> matches
) {
}
