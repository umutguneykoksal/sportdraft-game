package com.example.auction.common.sse;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SseHeartbeatScheduler {

    private final SseEmitterRegistry sseEmitterRegistry;

    public SseHeartbeatScheduler(SseEmitterRegistry sseEmitterRegistry) {
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Scheduled(fixedDelay = 5000)
    public void heartbeat() {
        for (UUID sessionId : sseEmitterRegistry.sessionIds()) {
            sseEmitterRegistry.broadcast(
                sessionId,
                "heartbeat",
                Map.of("now", Instant.now().toString())
            );
        }
    }
}
