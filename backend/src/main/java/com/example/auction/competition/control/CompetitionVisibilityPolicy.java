package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchStatus;
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
