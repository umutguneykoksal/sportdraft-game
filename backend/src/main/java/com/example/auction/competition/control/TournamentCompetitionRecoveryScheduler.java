package com.example.auction.competition.control;

import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSessionStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TournamentCompetitionRecoveryScheduler {

    private final ActiveSessionStore sessionStore;
    private final TournamentCompetitionRecoveryService recoveryService;

    public TournamentCompetitionRecoveryScheduler(
        ActiveSessionStore sessionStore,
        TournamentCompetitionRecoveryService recoveryService
    ) {
        this.sessionStore = sessionStore;
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedDelay = 3000)
    public void recoverMissingTournamentCompetitions() {
        sessionStore.findByStatus(SessionStatus.FINALIZING)
            .forEach(session -> recoveryService.recoverIfMissing(session.getId()));
        sessionStore.findByStatus(SessionStatus.AWAITING_RESULT)
            .forEach(session -> recoveryService.recoverIfMissing(session.getId()));
    }
}
