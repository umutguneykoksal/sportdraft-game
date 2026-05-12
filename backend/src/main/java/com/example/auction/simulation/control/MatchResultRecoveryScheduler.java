package com.example.auction.simulation.control;

import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
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
