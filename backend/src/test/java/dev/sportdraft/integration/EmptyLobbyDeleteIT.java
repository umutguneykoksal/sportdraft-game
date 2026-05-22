package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.common.realtime.RealtimeConnectionTracker;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.control.SessionDisconnectCleanupScheduler;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EmptyLobbyDeleteIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Autowired
    private RealtimeConnectionTracker connectionTracker;

    @Autowired
    private SessionDisconnectCleanupScheduler disconnectCleanupScheduler;

    @Test
    void soloWaitingLobby_playerDisconnect_deletesEmptySession() throws Exception {
        UUID user1 = UUID.randomUUID();

        UUID sessionId = lobbyService.createRoom(user1, "SoloHost").sessionId();
        assertThat(sessionStore.getById(sessionId).getStatus()).isEqualTo(SessionStatus.WAITING);
        assertThat(sessionStore.getById(sessionId).playerCount()).isEqualTo(1);

        connectionTracker.onPlayerConnected(sessionId, user1);
        connectionTracker.onPlayerFullyDisconnected(sessionId, user1);
        backdatePlayerDisconnect(sessionId, user1, Instant.now().minusSeconds(2));

        disconnectCleanupScheduler.cleanupDisconnectedSessions();

        assertThat(sessionStore.findById(sessionId)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private void backdatePlayerDisconnect(UUID sessionId, UUID userId, Instant disconnectedAt) throws Exception {
        Field field = RealtimeConnectionTracker.class.getDeclaredField("playerDisconnectedAt");
        field.setAccessible(true);
        ConcurrentHashMap<String, Instant> map = (ConcurrentHashMap<String, Instant>) field.get(connectionTracker);
        map.put(sessionId + ":" + userId, disconnectedAt);
    }
}
