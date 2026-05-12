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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private static final long TIMEOUT_MS = 30L * 60L * 1000L;
    /** Single SSE event name so browsers can use one EventSource.addEventListener. */
    public static final String SSE_EVENT_NAME = "game";

    private final Map<UUID, List<SseEmitter>> bySession = new ConcurrentHashMap<>();
    private final Set<SseEmitter> spectatorEmitters = ConcurrentHashMap.newKeySet();
    private final Map<SseEmitter, UUID> emitterToUserId = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> disconnectedAtBySession = new ConcurrentHashMap<>();
    /** Per-player disconnect tracking: key = "sessionId:userId" */
    private final Map<String, Instant> playerDisconnectedAt = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public SseEmitterRegistry(ObjectMapper objectMapper, WebSocketSessionRegistry webSocketSessionRegistry) {
        this.objectMapper = objectMapper;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    public SseEmitter register(UUID sessionId) {
        return register(sessionId, false, null);
    }

    public SseEmitter register(UUID sessionId, boolean spectator) {
        return register(sessionId, spectator, null);
    }

    public SseEmitter register(UUID sessionId, boolean spectator, UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(ex -> remove(sessionId, emitter));
        bySession.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        if (spectator) {
            spectatorEmitters.add(emitter);
        }
        if (userId != null) {
            emitterToUserId.put(emitter, userId);
        }
        if (!spectator) {
            disconnectedAtBySession.remove(sessionId);
            if (userId != null) {
                playerDisconnectedAt.remove(sessionId + ":" + userId);
                log.debug("SSE registered: session={} userId={} spectator={}", sessionId, userId, false);
            }
        }
        return emitter;
    }

    public int spectatorCount(UUID sessionId) {
        List<SseEmitter> emitters = bySession.getOrDefault(sessionId, List.of());
        return (int) emitters.stream().filter(spectatorEmitters::contains).count();
    }

    /**
     * Checks if a specific player in a session has been disconnected longer than the threshold.
     * Returns true only if NO active (non-spectator) emitters belong to that userId.
     */
    public boolean isPlayerDisconnectedLongerThan(UUID sessionId, UUID userId, Duration threshold) {
        List<SseEmitter> emitters = bySession.getOrDefault(sessionId, List.of());
        boolean hasActivePlayerEmitter = emitters.stream()
            .filter(e -> !spectatorEmitters.contains(e))
            .anyMatch(e -> userId.equals(emitterToUserId.get(e)));
        if (hasActivePlayerEmitter) {
            playerDisconnectedAt.remove(sessionId + ":" + userId);
            return false;
        }
        String key = sessionId + ":" + userId;
        Instant disconnectedAt = playerDisconnectedAt.computeIfAbsent(key, k -> Instant.now());
        boolean expired = disconnectedAt.plus(threshold).isBefore(Instant.now());
        long elapsedSec = Duration.between(disconnectedAt, Instant.now()).toSeconds();
        log.debug("Player disconnect check: session={} userId={} emitterCount={} hasActive={} disconnectedForSec={} expired={}",
            sessionId, userId, emitters.size(), false, elapsedSec, expired);
        return expired;
    }

    public void forgetPlayerDisconnect(UUID sessionId, UUID userId) {
        playerDisconnectedAt.remove(sessionId + ":" + userId);
    }

    public boolean hasActivePlayerEmitter(UUID sessionId, UUID userId) {
        return bySession.getOrDefault(sessionId, List.of()).stream()
            .filter(e -> !spectatorEmitters.contains(e))
            .anyMatch(e -> userId.equals(emitterToUserId.get(e)));
    }

    public Instant getPlayerDisconnectedAt(UUID sessionId, UUID userId) {
        return playerDisconnectedAt.get(sessionId + ":" + userId);
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
        boolean wasSpectator = spectatorEmitters.remove(emitter);
        UUID userId = emitterToUserId.remove(emitter);
        List<SseEmitter> emitters = bySession.get(sessionId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (!wasSpectator && userId != null) {
            boolean hasOtherPlayerEmitter = emitters.stream()
                .filter(e -> !spectatorEmitters.contains(e))
                .anyMatch(e -> userId.equals(emitterToUserId.get(e)));
            if (!hasOtherPlayerEmitter) {
                playerDisconnectedAt.putIfAbsent(sessionId + ":" + userId, Instant.now());
                log.info("SSE disconnected: session={} userId={} — disconnect timer started", sessionId, userId);
            }
        }
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
        String prefix = sessionId + ":";
        playerDisconnectedAt.keySet().removeIf(k -> k.startsWith(prefix));
        webSocketSessionRegistry.forgetSession(sessionId);
    }

    private void markDisconnectedIfNoActiveConnections(UUID sessionId) {
        if (!hasActiveConnections(sessionId)) {
            disconnectedAtBySession.putIfAbsent(sessionId, Instant.now());
        }
    }
}
