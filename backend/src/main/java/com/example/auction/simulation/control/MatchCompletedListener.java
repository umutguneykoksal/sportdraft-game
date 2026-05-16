package com.example.auction.simulation.control;

import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.control.SessionArchiver;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MatchCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(MatchCompletedListener.class);

    private final ActiveSessionStore sessionStore;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final SessionArchiver sessionArchiver;

    public MatchCompletedListener(
        ActiveSessionStore sessionStore,
        SseEmitterRegistry sseEmitterRegistry,
        SessionArchiver sessionArchiver
    ) {
        this.sessionStore = sessionStore;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.sessionArchiver = sessionArchiver;
    }

    @EventListener
    public void onMatchCompleted(MatchCompletedEvent event) {
        sessionStore.findById(event.sessionId()).ifPresent(session -> {
            long vBefore = session.getVersion();
            session.setHomeScore(event.homeScore());
            session.setAwayScore(event.awayScore());
            if (event.timeline() != null) {
                session.setMatchTimeline(event.timeline());
            }
            session.moveToFinished();
            sessionStore.saveIfVersionMatch(session, vBefore);
        });

        sseEmitterRegistry.broadcast(
            event.sessionId(),
            "match_completed",
            Map.of(
                "sessionId", event.sessionId(),
                "homeScore", event.homeScore(),
                "awayScore", event.awayScore(),
                "seed", event.seed()
            )
        );

        // Archival is handled by SessionDisconnectCleanupScheduler after a grace period
        // to allow clients time to poll the final state before the session is removed from Redis.
    }
}
