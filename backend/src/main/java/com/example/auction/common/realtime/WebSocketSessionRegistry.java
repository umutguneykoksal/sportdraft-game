package com.example.auction.common.realtime;

import java.net.URI;
import java.time.Duration;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    private final SessionRealtimeHub sessionRealtimeHub;
    private final RealtimeConnectionAuthorizer realtimeConnectionAuthorizer;
    private final RealtimeGateway realtimeGateway;
    private final RealtimeConnectionTracker connectionTracker;

    /** wsSession.getId() → game sessionId */
    private final Map<String, UUID> wsSessionToGameSession = new ConcurrentHashMap<>();
    /** wsSession.getId() → userId (for player-level disconnect tracking) */
    private final Map<String, UUID> wsSessionToUserId = new ConcurrentHashMap<>();

    public WebSocketSessionRegistry(
        SessionRealtimeHub sessionRealtimeHub,
        RealtimeConnectionAuthorizer realtimeConnectionAuthorizer,
        RealtimeGateway realtimeGateway,
        RealtimeConnectionTracker connectionTracker
    ) {
        this.sessionRealtimeHub = sessionRealtimeHub;
        this.realtimeConnectionAuthorizer = realtimeConnectionAuthorizer;
        this.realtimeGateway = realtimeGateway;
        this.connectionTracker = connectionTracker;
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

        realtimeConnectionAuthorizer.assertSessionMembership(sessionId, userId);
        sessionRealtimeHub.registerWs(sessionId, session);
        wsSessionToGameSession.put(session.getId(), sessionId);
        wsSessionToUserId.put(session.getId(), userId);
        connectionTracker.onPlayerConnected(sessionId, userId);
        log.debug("WS registered: session={} userId={}", sessionId, userId);
        return sessionId;
    }

    public void remove(UUID sessionId, WebSocketSession session) {
        UUID resolvedSessionId = sessionId == null ? wsSessionToGameSession.remove(session.getId()) : sessionId;
        if (resolvedSessionId == null) {
            wsSessionToGameSession.remove(session.getId());
        } else {
            wsSessionToGameSession.remove(session.getId());
        }
        UUID userId = wsSessionToUserId.remove(session.getId());
        sessionRealtimeHub.removeWs(resolvedSessionId, session);

        // Start forfeit timer if this player has no remaining WS connections for this session
        if (userId != null && resolvedSessionId != null) {
            boolean hasOtherWs = wsSessionToUserId.entrySet().stream()
                .anyMatch(e -> userId.equals(e.getValue())
                    && resolvedSessionId.equals(wsSessionToGameSession.get(e.getKey())));
            if (!hasOtherWs) {
                connectionTracker.onPlayerFullyDisconnected(resolvedSessionId, userId);
            }
        }
    }

    public void broadcast(UUID sessionId, String eventType, Object payload) {
        realtimeGateway.publish(sessionId, eventType, payload);
    }

    public List<UUID> sessionIds() {
        return sessionRealtimeHub.sessionIds();
    }

    public boolean hasActiveConnections(UUID sessionId) {
        return sessionRealtimeHub.hasActiveConnections(sessionId);
    }

    public boolean isDisconnectedLongerThan(UUID sessionId, Duration threshold) {
        return sessionRealtimeHub.isDisconnectedLongerThan(sessionId, threshold);
    }

    public void forgetSession(UUID sessionId) {
        sessionRealtimeHub.forgetSession(sessionId);
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
