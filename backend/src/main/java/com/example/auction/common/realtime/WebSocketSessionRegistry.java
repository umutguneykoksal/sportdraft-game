package com.example.auction.common.realtime;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
    private final RealtimePresenceChecker presenceChecker;

    private final Map<String, UUID> wsSessionToGameSession = new ConcurrentHashMap<>();
    private final Map<String, UUID> wsSessionToUserId = new ConcurrentHashMap<>();
    private final Set<String> spectatorWsIds = ConcurrentHashMap.newKeySet();

    public WebSocketSessionRegistry(
        SessionRealtimeHub sessionRealtimeHub,
        RealtimeConnectionAuthorizer realtimeConnectionAuthorizer,
        RealtimeGateway realtimeGateway,
        RealtimeConnectionTracker connectionTracker,
        @Lazy RealtimePresenceChecker presenceChecker
    ) {
        this.sessionRealtimeHub = sessionRealtimeHub;
        this.realtimeConnectionAuthorizer = realtimeConnectionAuthorizer;
        this.realtimeGateway = realtimeGateway;
        this.connectionTracker = connectionTracker;
        this.presenceChecker = presenceChecker;
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
        boolean spectator = Boolean.parseBoolean(params.getOrDefault("spectator", "false"));

        if (!spectator) {
            realtimeConnectionAuthorizer.assertSessionMembership(sessionId, userId);
            closeExistingPlayerWs(sessionId, userId, session.getId());
        }

        sessionRealtimeHub.registerWs(sessionId, session);
        wsSessionToGameSession.put(session.getId(), sessionId);
        wsSessionToUserId.put(session.getId(), userId);
        if (spectator) {
            spectatorWsIds.add(session.getId());
            log.debug("WS spectator registered: session={}", sessionId);
        } else {
            connectionTracker.onPlayerConnected(sessionId, userId);
            log.debug("WS registered: session={} userId={}", sessionId, userId);
        }
        return sessionId;
    }

    public void remove(UUID sessionId, WebSocketSession session) {
        UUID resolvedSessionId = wsSessionToGameSession.remove(session.getId());
        if (resolvedSessionId == null) {
            resolvedSessionId = sessionId;
        }
        UUID userId = wsSessionToUserId.remove(session.getId());
        boolean wasSpectator = spectatorWsIds.remove(session.getId());
        sessionRealtimeHub.removeWs(resolvedSessionId, session);

        if (!wasSpectator && userId != null && resolvedSessionId != null) {
            if (!presenceChecker.isPlayerPresent(resolvedSessionId, userId)) {
                connectionTracker.onPlayerFullyDisconnected(resolvedSessionId, userId);
            }
        }
    }

    public boolean hasNonSpectatorPlayerConnection(UUID sessionId, UUID userId) {
        return wsSessionToUserId.entrySet().stream()
            .anyMatch(e -> userId.equals(e.getValue())
                && sessionId.equals(wsSessionToGameSession.get(e.getKey()))
                && !spectatorWsIds.contains(e.getKey()));
    }

    private void closeExistingPlayerWs(UUID sessionId, UUID userId, String exceptWsId) {
        sessionRealtimeHub.getOpenWsSessions(sessionId).stream()
            .filter(ws -> !ws.getId().equals(exceptWsId))
            .filter(ws -> userId.equals(wsSessionToUserId.get(ws.getId())))
            .filter(ws -> !spectatorWsIds.contains(ws.getId()))
            .forEach(ws -> {
                log.debug("WS replacing existing connection: session={} userId={} wsId={}",
                    sessionId, userId, ws.getId());
                closeSilently(ws, CloseStatus.GOING_AWAY);
            });
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
