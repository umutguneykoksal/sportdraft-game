package dev.sportdraft.competition.control;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TournamentVisibilityPolicy implements CompetitionVisibilityPolicy {

    private final TournamentBracketService bracketService;

    TournamentVisibilityPolicy(TournamentBracketService bracketService) {
        this.bracketService = bracketService;
    }

    @Override
    public boolean mandatoryWatchPending(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        UUID viewerUserId,
        boolean watchedByMe
    ) {
        return TournamentRules.isMandatoryWatchPending(
            competition.getTournamentPhase(),
            match,
            viewerUserId,
            watchedByMe,
            bracketService.isTournamentParticipant(competition, viewerUserId)
        );
    }

    @Override
    public boolean scoresVisible(CompetitionEntity competition, CompetitionMatchEntity match, boolean watchedByMe) {
        if (match.getStatus() != CompetitionMatchStatus.COMPLETED) {
            return false;
        }
        return TournamentRules.scoresVisibleForViewer(
            competition.getTournamentPhase(),
            match.getRoundKey(),
            watchedByMe
        );
    }

    @Override
    public boolean replayAllowed(CompetitionEntity competition, CompetitionMatchEntity match) {
        return TournamentRules.canReplay(competition.getTournamentPhase(), match.getStatus());
    }

    @Override
    public int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match) {
        if (TournamentRules.isSemi(match.getRoundKey())) {
            return 2;
        }
        return bracketService.snapshotPlayerCount(competition);
    }
}
