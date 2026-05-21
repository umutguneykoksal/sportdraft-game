package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class LeagueVisibilityPolicy implements CompetitionVisibilityPolicy {

    @Override
    public boolean mandatoryWatchPending(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        UUID viewerUserId,
        boolean watchedByMe
    ) {
        return LeagueRules.isMandatoryWatchPending(
            competition.getLeaguePhase(),
            match,
            viewerUserId,
            watchedByMe
        );
    }

    @Override
    public boolean scoresVisible(CompetitionEntity competition, CompetitionMatchEntity match, boolean watchedByMe) {
        if (match.getStatus() != CompetitionMatchStatus.COMPLETED) {
            return false;
        }
        return LeagueRules.scoresVisibleForViewer(competition.getLeaguePhase(), match.getRoundKey(), watchedByMe);
    }

    @Override
    public boolean replayAllowed(CompetitionEntity competition, CompetitionMatchEntity match) {
        return LeagueRules.canReplay(competition.getLeaguePhase(), match.getStatus());
    }

    @Override
    public int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match) {
        return LeagueRules.PARTICIPANTS_PER_MATCH;
    }
}
