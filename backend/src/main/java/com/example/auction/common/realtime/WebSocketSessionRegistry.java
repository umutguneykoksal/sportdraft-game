package com.example.auction.common.realtime;

import com.example.auction.session.entity.SessionPlayerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WebSocketSessionRegistry {

    private final Map<UUID, List<WebSocketSession>> bySession = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> disconnectedAtBySession = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final SessionPlayerRepository sessionPlayerRepository;

    public WebSocketSessionRegistry(ObjectMapper objectMapper, SessionPlayerRepository sessionPlayerRepository) {
        this.objectMapper = objectMapper;
        this.sessionPlayerRepository = sessionPlayerRepository;
    }

    public UUID register(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            closeSilently(session, CloseStatus.BAD_DATA);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing websocket query params");
        }
        Map<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();
        UUID sessionId = parseUuid(params.get("sessionId"), "Missing or invalid sessionId");
        UUID userId = parseUuid(params.get("token"), "Missing or invalid token");

        if (!sessionPlayerRepository.existsBySession_IdAndUserId(sessionId, userId)) {
            closeSilently(session, CloseStatus.POLICY_VIOLATION);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is not part of this session");
        }
        bySession.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(session);
        disconnectedAtBySession.remove(sessionId);
        return sessionId;
    }

    public void remove(UUID sessionId, WebSocketSession session) {
        if (sessionId == null) {
            return;
        }
        List<WebSocketSession> sessions = bySession.get(sessionId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            bySession.remove(sessionId);
            markDisconnectedIfNoActiveConnections(sessionId);
        }
    }

    public void broadcast(UUID sessionId, String eventType, Object payload) {
        Map<String, Object> envelope = Map.of(
            "eventType", eventType,
            "occurredAt", Instant.now().toString(),
            "payload", payload
        );
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            return;
        }

        for (WebSocketSession session : bySession.getOrDefault(sessionId, List.of())) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                } else {
                    remove(sessionId, session);
                }
            } catch (IOException ex) {
                closeSilently(session, CloseStatus.SERVER_ERROR);
                remove(sessionId, session);
            }
        }
    }

    public List<UUID> sessionIds() {
        Set<UUID> ids = new HashSet<>(bySession.keySet());
        return List.copyOf(ids);
    }

    public boolean hasActiveConnections(UUID sessionId) {
        return bySession.getOrDefault(sessionId, List.of()).stream().anyMatch(WebSocketSession::isOpen);
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

    private UUID parseUuid(String raw, String message) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        }
    }

    private void closeSilently(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException ignored) {
            // no-op
        }
    }
}
