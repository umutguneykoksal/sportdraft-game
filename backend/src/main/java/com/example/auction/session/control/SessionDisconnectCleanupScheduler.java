package com.example.auction.session.control;

import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionDisconnectCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionDisconnectCleanupScheduler.class);

    private final ActiveSessionStore sessionStore;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final SessionArchiver sessionArchiver;
    private final Duration disconnectTimeout;

    public SessionDisconnectCleanupScheduler(
        ActiveSessionStore sessionStore,
        SseEmitterRegistry sseEmitterRegistry,
        SessionArchiver sessionArchiver,
        @Value("${app.session.disconnect-timeout-seconds:20}") long disconnectTimeoutSeconds
    ) {
        this.sessionStore = sessionStore;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.sessionArchiver = sessionArchiver;
        this.disconnectTimeout = Duration.ofSeconds(disconnectTimeoutSeconds);
    }

    @Scheduled(fixedDelay = 5000)
    public void cleanupDisconnectedSessions() {
        for (UUID sessionId : sessionStore.findAllActiveIds()) {
            if (sseEmitterRegistry.isDisconnectedLongerThan(sessionId, disconnectTimeout)) {
                sessionStore.delete(sessionId);
                sseEmitterRegistry.forgetSession(sessionId);
            }
        }
    }

    /** Retry archival for FINISHED sessions that are still stuck in Redis. */
    @Scheduled(fixedDelay = 10_000)
    public void archiveFinishedSessions() {
        for (ActiveSession session : sessionStore.findByStatus(SessionStatus.FINISHED)) {
            try {
                sessionArchiver.archive(session);
            } catch (Exception e) {
                log.warn("Archival retry failed for session {}", session.getId(), e);
            }
        }
    }
}
