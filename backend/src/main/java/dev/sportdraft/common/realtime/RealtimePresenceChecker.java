package dev.sportdraft.common.realtime;

import dev.sportdraft.common.sse.SseEmitterRegistry;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Unified "is this player still connected?" check for forfeit detection.
 * A player is present if they have a recent REST ping, a non-spectator SSE emitter,
 * or a non-spectator WebSocket session.
 */
@Component
public class RealtimePresenceChecker {

    private final RealtimeConnectionTracker connectionTracker;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public RealtimePresenceChecker(
        RealtimeConnectionTracker connectionTracker,
        @Lazy SseEmitterRegistry sseEmitterRegistry,
        @Lazy WebSocketSessionRegistry webSocketSessionRegistry
    ) {
        this.connectionTracker = connectionTracker;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    public boolean isPlayerPresent(UUID sessionId, UUID userId) {
        if (connectionTracker.hasRecentPing(sessionId, userId)) {
            return true;
        }
        if (sseEmitterRegistry.hasNonSpectatorPlayerConnection(sessionId, userId)) {
            return true;
        }
        return webSocketSessionRegistry.hasNonSpectatorPlayerConnection(sessionId, userId);
    }
}
