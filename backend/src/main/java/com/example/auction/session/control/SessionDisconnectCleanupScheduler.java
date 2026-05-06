package com.example.auction.session.control;

import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.GameSession;
import com.example.auction.session.entity.GameSessionRepository;
import com.example.auction.session.entity.SessionStatus;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SessionDisconnectCleanupScheduler {

    private final GameSessionRepository gameSessionRepository;
    private final SessionAdminService sessionAdminService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final Duration disconnectTimeout;

    public SessionDisconnectCleanupScheduler(
        GameSessionRepository gameSessionRepository,
        SessionAdminService sessionAdminService,
        SseEmitterRegistry sseEmitterRegistry,
        @Value("${app.session.disconnect-timeout-seconds:20}") long disconnectTimeoutSeconds
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.sessionAdminService = sessionAdminService;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.disconnectTimeout = Duration.ofSeconds(disconnectTimeoutSeconds);
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void cleanupDisconnectedSessions() {
        List<GameSession> activeSessions = gameSessionRepository.findByStatusNot(SessionStatus.FINISHED);
        for (GameSession session : activeSessions) {
            if (sseEmitterRegistry.isDisconnectedLongerThan(session.getId(), disconnectTimeout)) {
                sessionAdminService.deleteSession(session.getId());
                sseEmitterRegistry.forgetSession(session.getId());
            }
        }
    }
}
