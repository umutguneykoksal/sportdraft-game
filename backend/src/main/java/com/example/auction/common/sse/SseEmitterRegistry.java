package com.example.auction.common.sse;

import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT_MS = 30L * 60L * 1000L;
    private final Map<UUID, List<SseEmitter>> bySession = new ConcurrentHashMap<>();
    private final Map<UUID, List<WebSocketSession>> wsBySession = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> disconnectedAtBySession = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitterRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
                        .name(eventType)
                        .data(envelope)
                );
            } catch (IOException | IllegalStateException ex) {
                emitter.completeWithError(ex);
                remove(sessionId, emitter);
            } catch (Exception ex) {
                remove(sessionId, emitter);
            }
        }

        List<WebSocketSession> wsSessions = wsBySession.getOrDefault(sessionId, List.of());
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            return;
        }
        TextMessage textMessage = new TextMessage(jsonMessage);
        for (WebSocketSession wsSession : wsSessions) {
            try {
                if (wsSession.isOpen()) {
                    wsSession.sendMessage(textMessage);
                } else {
                    removeWebSocket(sessionId, wsSession);
                }
            } catch (IOException | IllegalStateException ex) {
                removeWebSocket(sessionId, wsSession);
            }
        }
    }

    public List<UUID> sessionIds() {
        Set<UUID> sessionIds = new HashSet<>(bySession.keySet());
        sessionIds.addAll(wsBySession.keySet());
        return List.copyOf(sessionIds);
    }

    public void registerWebSocket(UUID sessionId, WebSocketSession session) {
        wsBySession.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(session);
        disconnectedAtBySession.remove(sessionId);
    }

    public void removeWebSocket(UUID sessionId, WebSocketSession session) {
        List<WebSocketSession> sessions = wsBySession.get(sessionId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            wsBySession.remove(sessionId);
            markDisconnectedIfNoActiveConnections(sessionId);
        }
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
        return !bySession.getOrDefault(sessionId, List.of()).isEmpty()
            || !wsBySession.getOrDefault(sessionId, List.of()).isEmpty();
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
    }

    private void markDisconnectedIfNoActiveConnections(UUID sessionId) {
        if (!hasActiveConnections(sessionId)) {
            disconnectedAtBySession.putIfAbsent(sessionId, Instant.now());
        }
    }
}
