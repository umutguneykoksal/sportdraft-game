package com.example.auction.common.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tracks per-player and per-session disconnect/reconnect timing and REST-based keepalive pings.
 * Decoupled from emitter lifecycle: callers notify connect/disconnect events; this class
 * maintains timing state used by SessionDisconnectCleanupScheduler.
 */
@Component
public class RealtimeConnectionTracker {

    private static final Logger log = LoggerFactory.getLogger(RealtimeConnectionTracker.class);
    private static final Duration PING_ACTIVE_THRESHOLD = Duration.ofSeconds(30);

    /** key = "sessionId:userId" */
    private final Map<String, Instant> playerDisconnectedAt = new ConcurrentHashMap<>();
    /** key = "sessionId:userId" */
    private final Map<String, Instant> playerLastPingAt = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> sessionDisconnectedAt = new ConcurrentHashMap<>();

    // --- Connection lifecycle ---

    /** Call when a non-spectator player establishes a connection (SSE or WS). */
    public void onPlayerConnected(UUID sessionId, UUID userId) {
        sessionDisconnectedAt.remove(sessionId);
        playerDisconnectedAt.remove(playerKey(sessionId, userId));
        log.debug("Connection tracker: player connected session={} userId={}", sessionId, userId);
    }

    /**
     * Call when a non-spectator player has no remaining connections.
     * Starts the per-player disconnect timer.
     */
    public void onPlayerFullyDisconnected(UUID sessionId, UUID userId) {
        playerDisconnectedAt.putIfAbsent(playerKey(sessionId, userId), Instant.now());
        log.info("Connection tracker: player disconnected session={} userId={} — timer started", sessionId, userId);
    }

    /**
     * Call when all connections for a session (SSE + WS) are gone.
     * Starts the session-level disconnect timer.
     */
    public void onSessionFullyDisconnected(UUID sessionId) {
        sessionDisconnectedAt.putIfAbsent(sessionId, Instant.now());
    }

    // --- Keepalive ping ---

    /** Records a REST keepalive ping from a player; resets disconnect timer. */
    public void recordPing(UUID sessionId, UUID userId) {
        String key = playerKey(sessionId, userId);
        playerLastPingAt.put(key, Instant.now());
        playerDisconnectedAt.remove(key);
        sessionDisconnectedAt.remove(sessionId);
        log.debug("Connection tracker: ping recorded session={} userId={}", sessionId, userId);
    }

    public boolean hasRecentPing(UUID sessionId, UUID userId) {
        Instant last = playerLastPingAt.get(playerKey(sessionId, userId));
        return last != null && last.isAfter(Instant.now().minus(PING_ACTIVE_THRESHOLD));
    }

    public boolean hasAnyRecentPing(UUID sessionId) {
        String prefix = sessionId + ":";
        Instant cutoff = Instant.now().minus(PING_ACTIVE_THRESHOLD);
        return playerLastPingAt.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .anyMatch(e -> e.getValue().isAfter(cutoff));
    }

    // --- Disconnect checks ---

    /**
     * Returns true when a player's disconnect timer has exceeded the threshold
     * and no recent ping has been received.
     * Does NOT check active emitter/WS state — callers are responsible for that.
     */
    public boolean isPlayerDisconnectedLongerThan(UUID sessionId, UUID userId, Duration threshold) {
        if (hasRecentPing(sessionId, userId)) {
            playerDisconnectedAt.remove(playerKey(sessionId, userId));
            return false;
        }
        Instant disconnectedAt = playerDisconnectedAt.get(playerKey(sessionId, userId));
        if (disconnectedAt == null) return false;
        boolean expired = disconnectedAt.plus(threshold).isBefore(Instant.now());
        long elapsedSec = Duration.between(disconnectedAt, Instant.now()).toSeconds();
        log.debug("Player disconnect check session={} userId={} elapsedSec={} expired={}", sessionId, userId, elapsedSec, expired);
        return expired;
    }

    /**
     * Returns true when the session-level disconnect timer has exceeded the threshold
     * and no recent ping has been received from any player.
     * Callers must verify no active connections remain before calling.
     */
    public boolean isSessionDisconnectedLongerThan(UUID sessionId, Duration threshold) {
        if (hasAnyRecentPing(sessionId)) {
            sessionDisconnectedAt.remove(sessionId);
            return false;
        }
        Instant disconnectedAt = sessionDisconnectedAt.computeIfAbsent(sessionId, k -> Instant.now());
        return disconnectedAt.plus(threshold).isBefore(Instant.now());
    }

    public Instant getPlayerDisconnectedAt(UUID sessionId, UUID userId) {
        return playerDisconnectedAt.get(playerKey(sessionId, userId));
    }

    // --- Forget ---

    public void forgetPlayerDisconnect(UUID sessionId, UUID userId) {
        playerDisconnectedAt.remove(playerKey(sessionId, userId));
    }

    public void forgetSession(UUID sessionId) {
        sessionDisconnectedAt.remove(sessionId);
        String prefix = sessionId + ":";
        playerDisconnectedAt.keySet().removeIf(k -> k.startsWith(prefix));
        playerLastPingAt.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String playerKey(UUID sessionId, UUID userId) {
        return sessionId + ":" + userId;
    }
}
