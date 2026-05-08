package com.example.auction.common.sse;

import com.example.auction.common.realtime.WebSocketSessionRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT_MS = 30L * 60L * 1000L;
    /** Single SSE event name so browsers can use one EventSource.addEventListener. */
    public static final String SSE_EVENT_NAME = "game";

    private final Map<UUID, List<SseEmitter>> bySession = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> disconnectedAtBySession = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public SseEmitterRegistry(ObjectMapper objectMapper, WebSocketSessionRegistry webSocketSessionRegistry) {
        this.objectMapper = objectMapper;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    public SseEmitter register(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(ex -> remove(sessionId, emitter));
        bySession.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        disconnectedAtBySession.remove(sessionId);
        return emitter;
    }

    public void broadcast(UUID sessionId, String eventType, Object payload) {
        webSocketSessionRegistry.broadcast(sessionId, eventType, payload);
        Map<String, Object> envelope = Map.of(
            "eventType", eventType,
            "occurredAt", Instant.now().toString(),
            "payload", payload
        );
        List<SseEmitter> emitters = bySession.getOrDefault(sessionId, List.of());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name(SSE_EVENT_NAME)
                        .data(objectMapper.writeValueAsString(envelope))
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
        Set<UUID> sessionIds = new HashSet<>(bySession.keySet());
        sessionIds.addAll(webSocketSessionRegistry.sessionIds());
        return List.copyOf(sessionIds);
    }

    private void remove(UUID sessionId, SseEmitter emitter) {
        List<SseEmitter> emitters = bySession.get(sessionId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            bySession.remove(sessionId);
            markDisconnectedIfNoActiveConnections(sessionId);
        }
    }

    public boolean hasActiveConnections(UUID sessionId) {
        return !bySession.getOrDefault(sessionId, List.of()).isEmpty() || webSocketSessionRegistry.hasActiveConnections(sessionId);
    }

    public boolean isDisconnectedLongerThan(UUID sessionId, Duration threshold) {
        if (hasActiveConnections(sessionId)) {
            return false;
        }
        Instant disconnectedAt = disconnectedAtBySession.computeIfAbsent(sessionId, key -> Instant.now());
        return disconnectedAt.plus(threshold).isBefore(Instant.now());
    }

    public void forgetSession(UUID sessionId) {
        disconnectedAtBySession.remove(sessionId);
        webSocketSessionRegistry.forgetSession(sessionId);
    }

    private void markDisconnectedIfNoActiveConnections(UUID sessionId) {
        if (!hasActiveConnections(sessionId)) {
            disconnectedAtBySession.putIfAbsent(sessionId, Instant.now());
        }
    }
}
