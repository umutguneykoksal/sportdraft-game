package com.example.auction.session.control;

import com.example.auction.common.realtime.SessionEventLog;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private static final Set<SessionStatus> FORFEIT_ELIGIBLE_STATUSES = Set.of(
        SessionStatus.AUCTIONING,
        SessionStatus.MATCH_START_PENDING,
        SessionStatus.FORMATION_SELECT,
        SessionStatus.COUNTDOWN
    );

    private final ActiveSessionStore sessionStore;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final SessionArchiver sessionArchiver;
    private final SessionEventLog eventLog;
    private final Duration disconnectTimeout;

    public SessionDisconnectCleanupScheduler(
        ActiveSessionStore sessionStore,
        SseEmitterRegistry sseEmitterRegistry,
        SessionArchiver sessionArchiver,
        SessionEventLog eventLog,
        @Value("${app.session.disconnect-timeout-seconds:20}") long disconnectTimeoutSeconds
    ) {
        this.sessionStore = sessionStore;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.sessionArchiver = sessionArchiver;
        this.eventLog = eventLog;
        this.disconnectTimeout = Duration.ofSeconds(disconnectTimeoutSeconds);
    }

    @Scheduled(fixedDelay = 5000)
    public void cleanupDisconnectedSessions() {
        List<UUID> activeIds = sessionStore.findAllActiveIds();
        for (UUID sessionId : activeIds) {
            try {
                ActiveSession session = sessionStore.findById(sessionId).orElse(null);
                if (session == null) continue;

                // Per-player forfeit: if one player disconnected while the other is still connected
                if (session.playerCount() == 2 && FORFEIT_ELIGIBLE_STATUSES.contains(session.getStatus())) {
                    if (checkAndApplyForfeit(session)) continue;
                } else {
                    log.debug("Session {} skipping forfeit check: playerCount={} status={}",
                        sessionId, session.playerCount(), session.getStatus());
                }

                // Both disconnected — end as 0-0 draw and archive
                if (sseEmitterRegistry.isDisconnectedLongerThan(sessionId, disconnectTimeout)) {
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

    /** Retry archival for FINISHED sessions that are still stuck in Redis. */
    @Scheduled(fixedDelay = 10_000)
    public void archiveFinishedSessions() {
        for (ActiveSession session : sessionStore.findByStatus(SessionStatus.FINISHED)) {
            try {
                sessionArchiver.archive(session);
            } catch (Exception e) {
                log.warn("Archival retry failed for session {}", session.getId(), e);
            }
        }
    }
}
