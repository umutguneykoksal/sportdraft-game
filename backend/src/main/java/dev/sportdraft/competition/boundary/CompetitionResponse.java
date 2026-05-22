package dev.sportdraft.competition.boundary;

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
    List<CompetitionMatchResponse> matches,
    String leaguePhase,
    List<LeagueStandingRow> standings,
    List<UUID> coChampionUserIds,
    List<String> coChampionNicknames
) {
    public CompetitionResponse {
        matches = matches != null ? matches : List.of();
        standings = standings != null ? standings : List.of();
        coChampionUserIds = coChampionUserIds != null ? coChampionUserIds : List.of();
        coChampionNicknames = coChampionNicknames != null ? coChampionNicknames : List.of();
    }
}
