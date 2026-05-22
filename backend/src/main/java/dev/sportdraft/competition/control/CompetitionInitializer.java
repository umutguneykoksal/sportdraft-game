package dev.sportdraft.competition.control;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.simulation.control.DraftCompletedEvent;

@FunctionalInterface
interface CompetitionInitializer {
    CompetitionEntity initialize(DraftCompletedEvent event);
}
