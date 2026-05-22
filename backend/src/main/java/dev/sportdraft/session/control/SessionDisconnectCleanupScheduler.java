package dev.sportdraft.session.control;

import dev.sportdraft.common.realtime.SessionEventLog;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionDisconnectCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionDisconnectCleanupScheduler.class);
    private static final int FORFEIT_WINNER_SCORE = 3;
    private static final int FORFEIT_LOSER_SCORE = 0;

    private final ActiveSessionStore sessionStore;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final SessionArchiver sessionArchiver;
    private final SessionEventLog eventLog;
    private final Duration disconnectTimeout;
    private final Duration tournamentLobbyDisconnectTimeout;
    private final Duration tournamentRetention;

    /** Tracks when we first observed a session as FINISHED. */
    private final ConcurrentHashMap<UUID, Instant> finishedObservedAt = new ConcurrentHashMap<>();

    public SessionDisconnectCleanupScheduler(
        ActiveSessionStore sessionStore,
        SseEmitterRegistry sseEmitterRegistry,
        SessionArchiver sessionArchiver,
        SessionEventLog eventLog,
        @Value("${app.session.disconnect-timeout-seconds:20}") long disconnectTimeoutSeconds,
        @Value("${app.session.tournament-lobby-disconnect-timeout-seconds:240}") long tournamentLobbyDisconnectTimeoutSeconds,
        @Value("${app.session.tournament-retention-seconds:240}") long tournamentRetentionSeconds
    ) {
        this.sessionStore = sessionStore;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.sessionArchiver = sessionArchiver;
        this.eventLog = eventLog;
        this.disconnectTimeout = Duration.ofSeconds(disconnectTimeoutSeconds);
        this.tournamentLobbyDisconnectTimeout = Duration.ofSeconds(tournamentLobbyDisconnectTimeoutSeconds);
        this.tournamentRetention = Duration.ofSeconds(tournamentRetentionSeconds);
    }

    @Scheduled(fixedDelay = 5000)
    public void cleanupDisconnectedSessions() {
        List<UUID> activeIds = sessionStore.findAllActiveIds();
        for (UUID sessionId : activeIds) {
            try {
                ActiveSession session = sessionStore.findById(sessionId).orElse(null);
                if (session == null) continue;

                if (cleanupLobbyPhaseDisconnects(session)) {
                    continue;
                }

                // Per-player forfeit: if one player disconnected while the other is still connected
                if (session.playerCount() == 2 && SessionDisconnectPolicy.isForfeitEligible(session.getStatus())) {
                    if (checkAndApplyForfeit(session)) continue;
                } else {
                    log.debug("Session {} skipping forfeit check: playerCount={} status={}",
                        sessionId, session.playerCount(), session.getStatus());
                }

                // Do not auto-finish multi-player / post-draft sessions on disconnect.
                if (!SessionDisconnectPolicy.shouldSkipDisconnectDraw(session)
                    && sseEmitterRegistry.isDisconnectedLongerThan(sessionId, disconnectTimeout)) {
                    log.info("Both players disconnected for session {}, ending as 0-0 draw", sessionId);
                    long vBefore = session.getVersion();
                    session.setHomeScore(0);
                    session.setAwayScore(0);
                    session.moveToFinished();
                    sessionStore.saveIfVersionMatch(session, vBefore);
                    try { sessionArchiver.archive(session); }
                    catch (Exception ex) { log.warn("Failed to archive draw session {}", sessionId, ex); }
                    sseEmitterRegistry.forgetSession(sessionId);
                    eventLog.forget(sessionId);
                }
            } catch (Exception e) {
                log.warn("Error processing session {} in disconnect cleanup", sessionId, e);
            }
        }
    }

    private boolean checkAndApplyForfeit(ActiveSession session) {
        List<ActivePlayer> players = session.getPlayers();
        UUID sessionId = session.getId();
        ActivePlayer disconnectedPlayer = null;
        ActivePlayer connectedPlayer = null;

        for (ActivePlayer p : players) {
            boolean hasEmitter = sseEmitterRegistry.hasActivePlayerEmitter(sessionId, p.getUserId());
            boolean disconnected = false;
            if (hasEmitter) {
                sseEmitterRegistry.forgetPlayerDisconnect(sessionId, p.getUserId());
                connectedPlayer = p;
            } else {
                disconnected = sseEmitterRegistry.isPlayerDisconnectedLongerThan(
                    sessionId, p.getUserId(), disconnectTimeout);
                if (disconnected) {
                    disconnectedPlayer = p;
                } else {
                    connectedPlayer = p;
                }
            }
            log.info("Session {} player {} ({}) hasEmitter={} disconnected={}",
                sessionId, p.getNickname(), p.getUserId(), hasEmitter, disconnected);
        }

        // Both players disconnected past threshold → end as 0-0 draw
        if (disconnectedPlayer != null && connectedPlayer == null) {
            log.info("Both players disconnected past threshold in session {}, ending as 0-0 draw", sessionId);
            long vBefore = session.getVersion();
            session.setHomeScore(0);
            session.setAwayScore(0);
            session.moveToFinished();
            sessionStore.saveIfVersionMatch(session, vBefore);
            try { sessionArchiver.archive(session); }
            catch (Exception e) { log.warn("Failed to archive draw session {}", sessionId, e); }
            sseEmitterRegistry.forgetSession(sessionId);
            eventLog.forget(sessionId);
            return true;
        }

        // No disconnected player found
        if (disconnectedPlayer == null || connectedPlayer == null) return false;

        // One disconnected, one connected → forfeit
        log.info("Forfeit: player {} disconnected from session {}, awarding 3-0 to {}",
            disconnectedPlayer.getNickname(), sessionId, connectedPlayer.getNickname());

        long vBefore = session.getVersion();
        List<ActivePlayer> sorted = session.playersSortedById();
        boolean winnerIsHome = sorted.get(0).getUserId().equals(connectedPlayer.getUserId());
        session.setHomeScore(winnerIsHome ? FORFEIT_WINNER_SCORE : FORFEIT_LOSER_SCORE);
        session.setAwayScore(winnerIsHome ? FORFEIT_LOSER_SCORE : FORFEIT_WINNER_SCORE);
        session.moveToFinished();
        sessionStore.saveIfVersionMatch(session, vBefore);

        sseEmitterRegistry.broadcast(sessionId, "match_completed", Map.of(
            "sessionId", sessionId,
            "homeScore", session.getHomeScore(),
            "awayScore", session.getAwayScore(),
            "forfeit", true,
            "disconnectedPlayer", disconnectedPlayer.getNickname()
        ));

        // Archive
        try {
            sessionArchiver.archive(session);
        } catch (Exception e) {
            log.warn("Failed to archive forfeit session {} immediately, will be retried", sessionId, e);
        }

        sseEmitterRegistry.forgetSession(sessionId);
        eventLog.forget(sessionId);
        return true;
    }

    /**
     * Evicts disconnected players from pre-auction lobbies.
     * Tournament rooms use a longer timeout; an empty room is deleted immediately.
     */
    private boolean cleanupLobbyPhaseDisconnects(ActiveSession session) {
        if (!SessionDisconnectPolicy.isLobbyPhase(session.getStatus())) {
            return false;
        }

        Duration lobbyTimeout = SessionDisconnectPolicy.lobbyDisconnectTimeout(
            session, disconnectTimeout, tournamentLobbyDisconnectTimeout
        );
        UUID sessionId = session.getId();
        List<ActivePlayer> toRemove = new ArrayList<>();

        for (ActivePlayer player : session.getPlayers()) {
            if (sseEmitterRegistry.hasActivePlayerEmitter(sessionId, player.getUserId())) {
                sseEmitterRegistry.forgetPlayerDisconnect(sessionId, player.getUserId());
            } else if (sseEmitterRegistry.isPlayerDisconnectedLongerThan(
                sessionId, player.getUserId(), lobbyTimeout
            )) {
                toRemove.add(player);
            }
        }

        if (toRemove.isEmpty()
            && !session.getPlayers().isEmpty()
            && !sseEmitterRegistry.hasActiveConnections(sessionId)
            && sseEmitterRegistry.isDisconnectedLongerThan(sessionId, lobbyTimeout)) {
            toRemove.addAll(session.getPlayers());
        }

        if (toRemove.isEmpty()) {
            return false;
        }

        long versionBefore = session.getVersion();
        SessionStatus statusBefore = session.getStatus();
        for (ActivePlayer player : toRemove) {
            session.getPlayers().remove(player);
            log.info(
                "Removed disconnected player {} from lobby session {} ({})",
                player.getNickname(),
                sessionId,
                statusBefore
            );
        }

        if (session.playerCount() == 0) {
            sessionStore.delete(sessionId);
            sseEmitterRegistry.forgetSession(sessionId);
            eventLog.forget(sessionId);
            log.info("Deleted empty lobby session {}", sessionId);
            return true;
        }

        boolean needsReset = SessionDisconnectPolicy.shouldResetLobbyAfterLeave(session, statusBefore);
        if (needsReset) {
            session.moveToWaiting();
            sessionStore.saveIfVersionMatch(session, versionBefore);
            sseEmitterRegistry.broadcast(
                sessionId,
                "countdown_cancelled",
                Map.of("sessionId", sessionId, "remainingPlayers", session.playerCount())
            );
        } else {
            sessionStore.saveIfVersionMatch(session, versionBefore);
        }

        sseEmitterRegistry.broadcast(
            sessionId,
            "lobby_updated",
            Map.of(
                "sessionId", sessionId,
                "playerCount", session.playerCount(),
                "status", session.getStatus()
            )
        );
        return true;
    }

    /**
     * Archive FINISHED sessions after the grace period expires.
     * Tournament sessions are dropped from Redis after simulation completes; bracket data stays in Postgres.
     */
    @Scheduled(fixedDelay = 10_000)
    public void archiveFinishedSessions() {
        Instant now = Instant.now();

        for (ActiveSession session : sessionStore.findByStatus(SessionStatus.FINISHED)) {
            UUID sessionId = session.getId();
            Duration gracePeriod = SessionDisconnectPolicy.finishedGracePeriod(session, tournamentRetention);

            Instant firstObserved = finishedObservedAt.computeIfAbsent(sessionId, k -> now);

            Duration elapsed = Duration.between(firstObserved, now);
            if (elapsed.compareTo(gracePeriod) < 0) {
                log.debug("Session {} FINISHED but grace period not elapsed yet ({}s / {}s)",
                    sessionId, elapsed.toSeconds(), gracePeriod.toSeconds());
                continue;
            }

            try {
                if (SessionDisconnectPolicy.shouldArchiveTournamentWithoutPostgres(session)) {
                    sessionStore.delete(sessionId);
                    log.info("Removed FINISHED tournament session {} after {}s retention",
                        sessionId, gracePeriod.toSeconds());
                } else {
                    sessionArchiver.archive(session);
                    log.info("Archived FINISHED session {} after grace period", sessionId);
                }
                finishedObservedAt.remove(sessionId);
                sseEmitterRegistry.forgetSession(sessionId);
                eventLog.forget(sessionId);
            } catch (Exception e) {
                log.warn("Cleanup failed for session {}", sessionId, e);
            }
        }

        finishedObservedAt.keySet().removeIf(id -> !sessionStore.exists(id));
    }
}
