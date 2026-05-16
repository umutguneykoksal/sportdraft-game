package com.example.auction.common.sse;

import com.example.auction.common.realtime.RealtimeGateway;
import com.example.auction.common.realtime.SessionRealtimeHub;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends a lightweight heartbeat event every 5 s to all active sessions.
 * Heartbeats serve two purposes:
 *  1. Keep SSE/WS connections alive through proxy idle-timeout (typically 75–120 s).
 *  2. Allow clients to detect a broken connection quickly (missing heartbeats → reconnect).
 *
 * Heartbeats are NOT added to the event log ring buffer (seq = 0 in gateway).
 */
@Component
public class SseHeartbeatScheduler {

    private final SessionRealtimeHub sessionRealtimeHub;
    private final RealtimeGateway realtimeGateway;

    public SseHeartbeatScheduler(SessionRealtimeHub sessionRealtimeHub, RealtimeGateway realtimeGateway) {
        this.sessionRealtimeHub = sessionRealtimeHub;
        this.realtimeGateway = realtimeGateway;
    }

    @Scheduled(fixedDelay = 5000)
    public void heartbeat() {
        for (UUID sessionId : sessionRealtimeHub.sessionIds()) {
            realtimeGateway.publish(sessionId, "heartbeat", Map.of("now", Instant.now().toString()));
        }
    }
}
