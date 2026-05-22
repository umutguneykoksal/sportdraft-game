package com.example.auction.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.auction.common.realtime.RealtimeConnectionTracker;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.control.SessionDisconnectCleanupScheduler;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DuelDisconnectForfeitIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Autowired
    private RealtimeConnectionTracker connectionTracker;

    @Autowired
    private SessionDisconnectCleanupScheduler disconnectCleanupScheduler;

    @Test
    void duelDisconnectDuringAuction_awardsForfeitWin() throws Exception {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        UUID sessionId = lobbyService.createRoom(user1, "ForfeitHome").sessionId();
        lobbyService.joinRoom(sessionId, user2, "ForfeitAway");

        CompetitionTestFixtures.advanceToAuctioning(lobbyService, sessionStore, sessionId, user1, user2);
        assertThat(lobbyService.getSnapshot(sessionId, user1).status()).isEqualTo(SessionStatus.AUCTIONING);

        connectionTracker.onPlayerConnected(sessionId, user2);
        connectionTracker.onPlayerFullyDisconnected(sessionId, user1);
        backdatePlayerDisconnect(sessionId, user1, Instant.now().minusSeconds(2));

        await().atMost(3, SECONDS).until(() -> {
            disconnectCleanupScheduler.cleanupDisconnectedSessions();
            return sessionStore.findById(sessionId)
                .map(s -> s.getStatus() == SessionStatus.FINISHED)
                .orElse(false);
        });

        ActiveSession finished = sessionStore.getById(sessionId);
        assertThat(finished.getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(finished.getMatchWinnerUserId()).isEqualTo(user2);

        ActiveSession session = finished;
        boolean winnerIsHome = session.playersSortedById().get(0).getUserId().equals(user2);
        if (winnerIsHome) {
            assertThat(session.getHomeScore()).isEqualTo(3);
            assertThat(session.getAwayScore()).isEqualTo(0);
        } else {
            assertThat(session.getHomeScore()).isEqualTo(0);
            assertThat(session.getAwayScore()).isEqualTo(3);
        }
    }

    @SuppressWarnings("unchecked")
    private void backdatePlayerDisconnect(UUID sessionId, UUID userId, Instant disconnectedAt) throws Exception {
        Field field = RealtimeConnectionTracker.class.getDeclaredField("playerDisconnectedAt");
        field.setAccessible(true);
        ConcurrentHashMap<String, Instant> map = (ConcurrentHashMap<String, Instant>) field.get(connectionTracker);
        map.put(sessionId + ":" + userId, disconnectedAt);
    }
}
