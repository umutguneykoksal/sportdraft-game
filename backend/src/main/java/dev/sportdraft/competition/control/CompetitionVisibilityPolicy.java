package dev.sportdraft.competition.control;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import java.util.UUID;

interface CompetitionVisibilityPolicy {

    boolean mandatoryWatchPending(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        UUID viewerUserId,
        boolean watchedByMe
    );

    boolean scoresVisible(CompetitionEntity competition, CompetitionMatchEntity match, boolean watchedByMe);

    boolean replayAllowed(CompetitionEntity competition, CompetitionMatchEntity match);

    int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match);
}
