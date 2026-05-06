package com.example.auction.simulation.control;

import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.GameSession;
import com.example.auction.session.entity.GameSessionRepository;
import com.example.auction.session.entity.SessionStatus;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchCompletedListener {

    private final GameSessionRepository gameSessionRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    public MatchCompletedListener(GameSessionRepository gameSessionRepository, SseEmitterRegistry sseEmitterRegistry) {
        this.gameSessionRepository = gameSessionRepository;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchCompleted(MatchCompletedEvent event) {
        GameSession session = gameSessionRepository.findById(event.sessionId())
            .orElseThrow(() -> new IllegalStateException("Session not found for match completion"));

        if (session.getStatus() == SessionStatus.FINALIZING || session.getStatus() == SessionStatus.AWAITING_RESULT) {
            session.moveToFinished();
        }

        sseEmitterRegistry.broadcast(
            event.sessionId(),
            "match_completed",
            Map.of(
                "sessionId", event.sessionId(),
                "homeScore", event.homeScore(),
                "awayScore", event.awayScore(),
                "seed", event.seed()
            )
        );
    }
}
