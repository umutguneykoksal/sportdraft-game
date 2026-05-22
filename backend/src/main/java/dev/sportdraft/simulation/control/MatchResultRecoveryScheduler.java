package dev.sportdraft.simulation.control;

import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MatchResultRecoveryScheduler {

    private final ActiveSessionStore sessionStore;
    private final MatchResultRecoveryService matchResultRecoveryService;

    public MatchResultRecoveryScheduler(
        ActiveSessionStore sessionStore,
        MatchResultRecoveryService matchResultRecoveryService
    ) {
        this.sessionStore = sessionStore;
        this.matchResultRecoveryService = matchResultRecoveryService;
    }

    @Scheduled(fixedDelay = 3000)
    public void recoverMissingMatchResults() {
        sessionStore.findByStatus(SessionStatus.FINALIZING)
            .forEach(session -> matchResultRecoveryService.recoverIfMissing(session.getId()));
        sessionStore.findByStatus(SessionStatus.AWAITING_RESULT)
            .forEach(session -> matchResultRecoveryService.recoverIfMissing(session.getId()));
    }
}
