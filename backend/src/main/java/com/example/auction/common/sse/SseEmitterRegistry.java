package com.example.auction.common.sse;

import com.example.auction.common.realtime.RealtimeConnectionTracker;
import com.example.auction.common.realtime.RealtimeGateway;
import com.example.auction.common.realtime.SessionRealtimeHub;
import java.time.Duration;
import java.time.Instant;
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

/**
 * Manages SSE emitter lifecycle: registration, spectator tracking, and per-emitter cleanup.
 * Broadcast is delegated to RealtimeGateway (single broadcast path for SSE + WS).
 * Disconnect/ping timing is delegated to RealtimeConnectionTracker.
 * Emitters are registered in both this registry (for player/spectator tracking)
 * and SessionRealtimeHub (for broadcasting).
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private static final long TIMEOUT_MS = 30L * 60L * 1000L;

    /** Per-session list of active SSE emitters; used for spectator counting and per-player checks. */
    private final Map<UUID, List<SseEmitter>> bySession = new ConcurrentHashMap<>();
    private final Set<SseEmitter> spectatorEmitters = ConcurrentHashMap.newKeySet();
    private final Map<SseEmitter, UUID> emitterToUserId = new ConcurrentHashMap<>();

    private final RealtimeGateway realtimeGateway;
    private final SessionRealtimeHub sessionRealtimeHub;
    private final RealtimeConnectionTracker connectionTracker;

    public SseEmitterRegistry(
        RealtimeGateway realtimeGateway,
        SessionRealtimeHub sessionRealtimeHub,
        RealtimeConnectionTracker connectionTracker
    ) {
        this.realtimeGateway = realtimeGateway;
        this.sessionRealtimeHub = sessionRealtimeHub;
        this.connectionTracker = connectionTracker;
    }

    public SseEmitter register(UUID sessionId) {
        return register(sessionId, false, null);
    }

    public SseEmitter register(UUID sessionId, boolean spectator) {
        return register(sessionId, spectator, null);
    }

    /**
     * Registers an SSE emitter for a session.
     * If a non-spectator player already has an active emitter for this session,
     * the old emitter is closed first (single active connection per player).
     */
    public SseEmitter register(UUID sessionId, boolean spectator, UUID userId) {
        if (!spectator && userId != null) {
            closeExistingPlayerEmitters(sessionId, userId);
        }

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(ex -> remove(sessionId, emitter));

        bySession.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        sessionRealtimeHub.registerSse(sessionId, emitter);

        if (spectator) {
            spectatorEmitters.add(emitter);
        }
        if (userId != null) {
            emitterToUserId.put(emitter, userId);
        }
        if (!spectator && userId != null) {
            connectionTracker.onPlayerConnected(sessionId, userId);
            log.debug("SSE registered: session={} userId={}", sessionId, userId);
        }
        return emitter;
    }

    /** Closes any existing non-spectator emitters for the given player in this session. */
    private void closeExistingPlayerEmitters(UUID sessionId, UUID userId) {
        List<SseEmitter> existing = bySession.getOrDefault(sessionId, List.of());
        existing.stream()
            .filter(e -> !spectatorEmitters.contains(e))
            .filter(e -> userId.equals(emitterToUserId.get(e)))
            .toList()
            .forEach(e -> {
                log.debug("SSE replacing existing emitter: session={} userId={}", sessionId, userId);
                e.complete();
            });
    }

    public int spectatorCount(UUID sessionId) {
        List<SseEmitter> emitters = bySession.getOrDefault(sessionId, List.of());
        return (int) emitters.stream().filter(spectatorEmitters::contains).count();
    }

    /** Delegates broadcast entirely to the unified RealtimeGateway (SSE + WS). */
    public void broadcast(UUID sessionId, String eventType, Object payload) {
        realtimeGateway.publish(sessionId, eventType, payload);
    }

    public List<UUID> sessionIds() {
        return sessionRealtimeHub.sessionIds();
    }

    // --- Connection state queries (used by SessionDisconnectCleanupScheduler) ---

    public boolean hasActiveConnections(UUID sessionId) {
        return sessionRealtimeHub.hasActiveConnections(sessionId);
    }

    /**
     * True if a player has an active SSE emitter OR a recent REST ping.
     * Used by forfeit detection to determine if a player is still present.
     */
    public boolean hasActivePlayerEmitter(UUID sessionId, UUID userId) {
        if (connectionTracker.hasRecentPing(sessionId, userId)) {
            return true;
        }
        return bySession.getOrDefault(sessionId, List.of()).stream()
            .filter(e -> !spectatorEmitters.contains(e))
            .anyMatch(e -> userId.equals(emitterToUserId.get(e)));
    }

    /**
     * True if a player has been disconnected (no active emitter or ping) longer than the threshold.
     */
    public boolean isPlayerDisconnectedLongerThan(UUID sessionId, UUID userId, Duration threshold) {
        if (hasActivePlayerEmitter(sessionId, userId)) {
            connectionTracker.forgetPlayerDisconnect(sessionId, userId);
            return false;
        }
        return connectionTracker.isPlayerDisconnectedLongerThan(sessionId, userId, threshold);
    }

    /**
     * True if the session has had no connections (SSE or WS) for longer than the threshold.
     */
    public boolean isDisconnectedLongerThan(UUID sessionId, Duration threshold) {
        if (hasActiveConnections(sessionId)) {
            return false;
        }
        return connectionTracker.isSessionDisconnectedLongerThan(sessionId, threshold);
    }

    public Instant getPlayerDisconnectedAt(UUID sessionId, UUID userId) {
        return connectionTracker.getPlayerDisconnectedAt(sessionId, userId);
    }

    // --- Keepalive ping ---

    /** Records a REST keepalive ping from a player. */
    public void recordPlayerPing(UUID sessionId, UUID userId) {
        connectionTracker.recordPing(sessionId, userId);
    }

    public void forgetPlayerDisconnect(UUID sessionId, UUID userId) {
        connectionTracker.forgetPlayerDisconnect(sessionId, userId);
    }

    public void forgetSession(UUID sessionId) {
        connectionTracker.forgetSession(sessionId);
        sessionRealtimeHub.forgetSession(sessionId);
        // Event log is cleaned by SessionEventLog.forget called from SessionDisconnectCleanupScheduler
        // to keep this class free of event log dependency.
    }

    private void remove(UUID sessionId, SseEmitter emitter) {
        boolean wasSpectator = spectatorEmitters.remove(emitter);
        UUID userId = emitterToUserId.remove(emitter);
        sessionRealtimeHub.removeSse(sessionId, emitter);

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
                connectionTracker.onPlayerFullyDisconnected(sessionId, userId);
            }
        }

        if (emitters.isEmpty()) {
            bySession.remove(sessionId);
            if (!hasActiveConnections(sessionId)) {
                connectionTracker.onSessionFullyDisconnected(sessionId);
            }
        }
    }
}
