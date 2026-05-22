package dev.sportdraft.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealtimeConnectionTrackerTest {

    private RealtimeConnectionTracker tracker;
    private UUID sessionId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tracker = new RealtimeConnectionTracker();
        sessionId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void disconnectExpiredAfterThreshold_withoutRecentPing() throws Exception {
        tracker.onPlayerFullyDisconnected(sessionId, userId);
        backdateDisconnect(sessionId, userId, Instant.now().minusSeconds(5));

        assertThat(tracker.isPlayerDisconnectedLongerThan(sessionId, userId, Duration.ofSeconds(1)))
            .isTrue();
    }

    @Test
    void recentPingResetsDisconnectTimer() {
        tracker.onPlayerFullyDisconnected(sessionId, userId);
        tracker.recordPing(sessionId, userId);

        assertThat(tracker.isPlayerDisconnectedLongerThan(sessionId, userId, Duration.ofSeconds(1)))
            .isFalse();
        assertThat(tracker.getPlayerDisconnectedAt(sessionId, userId)).isNull();
    }

    @Test
    void connectClearsPlayerDisconnectState() {
        tracker.onPlayerFullyDisconnected(sessionId, userId);
        tracker.onPlayerConnected(sessionId, userId);

        assertThat(tracker.getPlayerDisconnectedAt(sessionId, userId)).isNull();
    }

    @Test
    void forgetSessionClearsAllPlayerState() {
        tracker.onPlayerFullyDisconnected(sessionId, userId);
        tracker.recordPing(sessionId, userId);
        tracker.onSessionFullyDisconnected(sessionId);

        tracker.forgetSession(sessionId);

        assertThat(tracker.getPlayerDisconnectedAt(sessionId, userId)).isNull();
        assertThat(tracker.hasRecentPing(sessionId, userId)).isFalse();
    }

    private void backdateDisconnect(UUID sessionId, UUID userId, Instant disconnectedAt) throws Exception {
        var field = RealtimeConnectionTracker.class.getDeclaredField("playerDisconnectedAt");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var map = (java.util.concurrent.ConcurrentHashMap<String, Instant>) field.get(tracker);
        map.put(sessionId + ":" + userId, disconnectedAt);
    }
}
