package dev.sportdraft.competition.control;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.competition.entity.TournamentPhase;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.simulation.entity.SimulationResult;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class TournamentMatchModeHooks implements CompetitionMatchModeHooks {

    private final TournamentBracketService bracketService;
    private final CompetitionRepository competitionRepository;
    private final TournamentProgressNotifier progressNotifier;
    private final TournamentVisibilityPolicy visibilityPolicy;
    private final ActiveSessionStore sessionStore;

    TournamentMatchModeHooks(
        TournamentBracketService bracketService,
        CompetitionRepository competitionRepository,
        TournamentProgressNotifier progressNotifier,
        TournamentVisibilityPolicy visibilityPolicy,
        ActiveSessionStore sessionStore
    ) {
        this.bracketService = bracketService;
        this.competitionRepository = competitionRepository;
        this.progressNotifier = progressNotifier;
        this.visibilityPolicy = visibilityPolicy;
        this.sessionStore = sessionStore;
    }

    @Override
    public CompetitionType type() {
        return CompetitionType.TOURNAMENT;
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
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Replays unlock after everyone watches the final"
                );
            }
            return;
        }
        if (competition.getTournamentPhase() == TournamentPhase.INTERMISSION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Semi-final results — final starts shortly");
        }
        if (TournamentRules.isSemi(match.getRoundKey()) && !bracketService.isMatchParticipant(match, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only watch your own semi-final");
        }
        if (!visibilityPolicy.mandatoryWatchPending(competition, match, userId, watched)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This match is not available to watch right now");
        }
    }

    @Override
    public boolean isReplaysPhase(CompetitionEntity competition) {
        return competition.getTournamentPhase() == TournamentPhase.REPLAYS;
    }

    @Override
    public SimulationResult simulateIfNeeded(CompetitionEntity competition, CompetitionMatchEntity match) {
        return bracketService.simulateMatchIfNeeded(competition, match);
    }

    @Override
    public CompetitionDraftSnapshot readSnapshot(CompetitionEntity competition) {
        return bracketService.readSnapshot(competition);
    }

    @Override
    public int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match) {
        return visibilityPolicy.requiredWatchers(competition, match);
    }

    @Override
    public boolean matchFullyWatched(CompetitionEntity competition, CompetitionMatchEntity match) {
        return bracketService.matchFullyWatched(competition, match);
    }

    @Override
    public CompetitionVisibilityPolicy visibilityPolicy() {
        return visibilityPolicy;
    }

    @Override
    public CompetitionEntity afterWatchRecorded(UUID sessionId, CompetitionEntity competition) {
        if (bracketService.tryStartIntermissionIfReady(competition)) {
            competition = competitionRepository.findById(competition.getId()).orElse(competition);
            progressNotifier.notifyIntermissionStarted(sessionId, competition);
        }

        CompetitionEntity updated = bracketService.tryCompleteTournament(competition);
        updated = competitionRepository.findById(updated.getId()).orElse(updated);
        progressNotifier.notifyFinalState(sessionId, updated, () ->
            CompetitionMatchSupport.finishSessionIfPresent(sessionStore, sessionId)
        );
        return updated;
    }
}
