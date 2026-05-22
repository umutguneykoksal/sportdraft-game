package dev.sportdraft.simulation.control;

import dev.sportdraft.simulation.entity.MatchEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchCompletedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    int homeScore,
    int awayScore,
    long seed,
    List<MatchEvent> timeline,
    int homePenaltyGoals,
    int awayPenaltyGoals,
    UUID winnerUserId
) {
}
