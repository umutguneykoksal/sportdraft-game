package com.example.auction.common.sse;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT_MS = 30L * 60L * 1000L;
    private final Map<UUID, List<SseEmitter>> bySession = new ConcurrentHashMap<>();

    public SseEmitter register(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(ex -> remove(sessionId, emitter));
        bySession.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        return emitter;
    }

    public void broadcast(UUID sessionId, String eventType, Object payload) {
        List<SseEmitter> emitters = bySession.getOrDefault(sessionId, List.of());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name(eventType)
                        .data(
                            Map.of(
                                "eventType", eventType,
                                "occurredAt", Instant.now().toString(),
                                "payload", payload
                            )
                        )
                );
            } catch (IOException | IllegalStateException ex) {
                emitter.completeWithError(ex);
                remove(sessionId, emitter);
            } catch (Exception ex) {
                remove(sessionId, emitter);
            }
        }
    }

    public List<UUID> sessionIds() {
        return List.copyOf(bySession.keySet());
    }

    private void remove(UUID sessionId, SseEmitter emitter) {
        List<SseEmitter> emitters = bySession.get(sessionId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            bySession.remove(sessionId);
        }
    }
}
