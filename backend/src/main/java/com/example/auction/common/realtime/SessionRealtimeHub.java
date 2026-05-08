package com.example.auction.common.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class SessionRealtimeHub {

    private final Map<UUID, List<SseEmitter>> sseBySession = new ConcurrentHashMap<>();
    private final Map<UUID, List<WebSocketSession>> wsBySession = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> disconnectedAtBySession = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SessionRealtimeHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void registerSse(UUID sessionId, SseEmitter emitter) {
        sseBySession.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        disconnectedAtBySession.remove(sessionId);
    }

    public void registerWs(UUID sessionId, WebSocketSession session) {
        wsBySession.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(session);
        disconnectedAtBySession.remove(sessionId);
    }

    public void removeSse(UUID sessionId, SseEmitter emitter) {
        removeFromMap(sseBySession, sessionId, emitter);
    }

    public void removeWs(UUID sessionId, WebSocketSession session) {
        removeFromMap(wsBySession, sessionId, session);
    }

    public void publish(UUID sessionId, Map<String, Object> envelope) {
        publishSse(sessionId, envelope);
        publishWs(sessionId, envelope);
    }

    public List<UUID> sessionIds() {
        Set<UUID> ids = new HashSet<>(sseBySession.keySet());
        ids.addAll(wsBySession.keySet());
        return List.copyOf(ids);
    }

    public boolean hasActiveConnections(UUID sessionId) {
        return !sseBySession.getOrDefault(sessionId, List.of()).isEmpty()
            || wsBySession.getOrDefault(sessionId, List.of()).stream().anyMatch(WebSocketSession::isOpen);
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

    private void publishSse(UUID sessionId, Map<String, Object> envelope) {
        List<SseEmitter> emitters = sseBySession.getOrDefault(sessionId, List.of());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("game")
                        .id(String.valueOf(envelope.get("eventId")))
                        .data(objectMapper.writeValueAsString(envelope))
                );
            } catch (Exception ex) {
                emitter.completeWithError(ex);
                removeSse(sessionId, emitter);
            }
        }
    }

    private void publishWs(UUID sessionId, Map<String, Object> envelope) {
        List<WebSocketSession> sessions = wsBySession.getOrDefault(sessionId, List.of());
        for (WebSocketSession session : sessions) {
            try {
                if (!session.isOpen()) {
                    removeWs(sessionId, session);
                    continue;
                }
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
            } catch (IOException ex) {
                removeWs(sessionId, session);
            }
        }
    }

    private <T> void removeFromMap(Map<UUID, List<T>> map, UUID sessionId, T value) {
        if (sessionId == null) {
            return;
        }
        List<T> list = map.get(sessionId);
        if (list == null) {
            return;
        }
        list.remove(value);
        if (list.isEmpty()) {
            map.remove(sessionId);
            if (!hasActiveConnections(sessionId)) {
                disconnectedAtBySession.putIfAbsent(sessionId, Instant.now());
            }
        }
    }
}
