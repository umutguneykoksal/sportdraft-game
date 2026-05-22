package dev.sportdraft.competition.control;

import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSessionStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TournamentCompetitionRecoveryScheduler {

    private final ActiveSessionStore sessionStore;
    private final TournamentCompetitionRecoveryService tournamentRecoveryService;
    private final LeagueCompetitionRecoveryService leagueRecoveryService;

    public TournamentCompetitionRecoveryScheduler(
        ActiveSessionStore sessionStore,
        TournamentCompetitionRecoveryService tournamentRecoveryService,
        LeagueCompetitionRecoveryService leagueRecoveryService
    ) {
        this.sessionStore = sessionStore;
        this.tournamentRecoveryService = tournamentRecoveryService;
        this.leagueRecoveryService = leagueRecoveryService;
    }

    @Scheduled(fixedDelay = 3000)
    public void recoverMissingCompetitions() {
        sessionStore.findByStatus(SessionStatus.FINALIZING)
            .forEach(session -> {
                tournamentRecoveryService.recoverIfMissing(session.getId());
                leagueRecoveryService.recoverIfMissing(session.getId());
            });
        sessionStore.findByStatus(SessionStatus.AWAITING_RESULT)
            .forEach(session -> {
                tournamentRecoveryService.recoverIfMissing(session.getId());
                leagueRecoveryService.recoverIfMissing(session.getId());
            });
    }
}
