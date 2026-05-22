package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionType;
import com.example.auction.competition.entity.LeaguePhase;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.entity.SimulationResult;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class LeagueMatchModeHooks implements CompetitionMatchModeHooks {

    private final LeagueFixtureService leagueService;
    private final CompetitionRepository competitionRepository;
    private final LeagueProgressNotifier progressNotifier;
    private final LeagueVisibilityPolicy visibilityPolicy;
    private final ActiveSessionStore sessionStore;

    LeagueMatchModeHooks(
        LeagueFixtureService leagueService,
        CompetitionRepository competitionRepository,
        LeagueProgressNotifier progressNotifier,
        LeagueVisibilityPolicy visibilityPolicy,
        ActiveSessionStore sessionStore
    ) {
        this.leagueService = leagueService;
        this.competitionRepository = competitionRepository;
        this.progressNotifier = progressNotifier;
        this.visibilityPolicy = visibilityPolicy;
        this.sessionStore = sessionStore;
    }

    @Override
    public CompetitionType type() {
        return CompetitionType.LEAGUE;
    }

    @Override
    public void validateOpen(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        UUID userId,
        boolean replay,
        boolean watched
    ) {
        if (replay) {
            if (!visibilityPolicy.replayAllowed(competition, match)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Replays unlock after the league completes");
            }
            return;
        }
        if (competition.getLeaguePhase().isMatchdayBreak()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Matchday break — next round starts shortly");
        }
        if (!visibilityPolicy.mandatoryWatchPending(competition, match, userId, watched)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This match is not available to watch right now");
        }
    }

    @Override
    public boolean isReplaysPhase(CompetitionEntity competition) {
        return competition.getLeaguePhase() == LeaguePhase.REPLAYS;
    }

    @Override
    public SimulationResult simulateIfNeeded(CompetitionEntity competition, CompetitionMatchEntity match) {
        return leagueService.simulateMatchIfNeeded(competition, match);
    }

    @Override
    public CompetitionDraftSnapshot readSnapshot(CompetitionEntity competition) {
        return leagueService.readSnapshot(competition);
    }

    @Override
    public int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match) {
        return visibilityPolicy.requiredWatchers(competition, match);
    }

    @Override
    public boolean matchFullyWatched(CompetitionEntity competition, CompetitionMatchEntity match) {
        return leagueService.matchFullyWatched(competition, match);
    }

    @Override
    public CompetitionVisibilityPolicy visibilityPolicy() {
        return visibilityPolicy;
    }

    @Override
    public CompetitionEntity afterWatchRecorded(UUID sessionId, CompetitionEntity competition) {
        boolean breakStarted = leagueService.tryStartMatchdayBreak(competition);
        competition = competitionRepository.findById(competition.getId()).orElse(competition);
        progressNotifier.notifyBreakOrPlayoffIfStarted(sessionId, competition, breakStarted);

        if (competition.getLeaguePhase() == LeaguePhase.CHAMPIONSHIP_PLAYOFF) {
            competition = leagueService.tryCompletePlayoff(competition);
        }

        progressNotifier.notifyFinalState(sessionId, competition, () ->
            CompetitionMatchSupport.finishSessionIfPresent(sessionStore, sessionId)
        );
        return competition;
    }
}
