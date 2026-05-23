package dev.sportdraft.competition.control;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.simulation.entity.SimulationResult;
import java.util.UUID;

interface CompetitionModeBehavior {

    CompetitionType type();

    void validateOpen(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        UUID userId,
        boolean replay,
        boolean watched
    );

    boolean isReplaysPhase(CompetitionEntity competition);

    SimulationResult simulateIfNeeded(CompetitionEntity competition, CompetitionMatchEntity match);

    CompetitionDraftSnapshot readSnapshot(CompetitionEntity competition);

    int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match);

    boolean matchFullyWatched(CompetitionEntity competition, CompetitionMatchEntity match);

    CompetitionVisibility visibility();

    CompetitionEntity afterWatchRecorded(UUID sessionId, CompetitionEntity competition);
}
