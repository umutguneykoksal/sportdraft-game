package com.example.auction.simulation.control;

import com.example.auction.session.entity.GameSessionRepository;
import com.example.auction.session.entity.SessionStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MatchResultRecoveryScheduler {

    private final GameSessionRepository gameSessionRepository;
    private final MatchResultRecoveryService matchResultRecoveryService;

    public MatchResultRecoveryScheduler(
        GameSessionRepository gameSessionRepository,
        MatchResultRecoveryService matchResultRecoveryService
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.matchResultRecoveryService = matchResultRecoveryService;
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void recoverMissingMatchResults() {
        recoverByStatus(SessionStatus.FINALIZING);
        recoverByStatus(SessionStatus.AWAITING_RESULT);
    }

    private void recoverByStatus(SessionStatus status) {
        gameSessionRepository.findByStatusOrderByCreatedAtAsc(status)
            .forEach(session -> matchResultRecoveryService.recoverIfMissing(session.getId()));
    }
}
