package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionType;
import com.example.auction.simulation.entity.SimulationResult;
import java.util.UUID;

interface CompetitionMatchModeHooks {

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

    TournamentDraftSnapshot readSnapshot(CompetitionEntity competition);

    int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match);

    boolean matchFullyWatched(CompetitionEntity competition, CompetitionMatchEntity match);

    CompetitionVisibilityPolicy visibilityPolicy();

    CompetitionEntity afterWatchRecorded(UUID sessionId, CompetitionEntity competition);
}
