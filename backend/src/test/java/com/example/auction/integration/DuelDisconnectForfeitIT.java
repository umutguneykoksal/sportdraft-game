package com.example.auction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auction.common.realtime.RealtimeConnectionTracker;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.control.SessionDisconnectCleanupScheduler;
import com.example.auction.session.entity.MatchHistoryRepository;
import com.example.auction.session.entity.SessionStatus;
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

    @Autowired
    private MatchHistoryRepository matchHistoryRepository;

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

        disconnectCleanupScheduler.cleanupDisconnectedSessions();

        assertThat(sessionStore.findById(sessionId)).isEmpty();
        var history = matchHistoryRepository.findBySessionId(sessionId).orElseThrow();
        boolean winnerIsHome = history.getHomeUserId().equals(user2);
        if (winnerIsHome) {
            assertThat(history.getHomeScore()).isEqualTo(3);
            assertThat(history.getAwayScore()).isEqualTo(0);
        } else {
            assertThat(history.getHomeScore()).isEqualTo(0);
            assertThat(history.getAwayScore()).isEqualTo(3);
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
