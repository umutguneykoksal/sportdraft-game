package dev.sportdraft.competition.boundary;

import dev.sportdraft.simulation.entity.MatchEvent;
import java.util.List;
import java.util.UUID;

public record CompetitionMatchOpenResponse(
    UUID matchId,
    String roundKey,
    UUID homeUserId,
    String homeNickname,
    UUID awayUserId,
    String awayNickname,
    String homeFormation,
    String awayFormation,
    List<MatchEvent> timeline,
    Integer homeScore,
    Integer awayScore,
    Integer homePenaltyScore,
    Integer awayPenaltyScore,
    boolean watchedByMe,
    boolean replay,
    boolean allPlayersWatched,
    long watchCount,
    int requiredWatchers
) {
}
