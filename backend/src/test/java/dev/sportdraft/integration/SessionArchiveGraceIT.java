package dev.sportdraft.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.bidding.control.CardCatalogService;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.control.SessionDisconnectCleanupScheduler;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.entity.MatchHistoryRepository;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SessionArchiveGraceIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private CardCatalogService cardCatalog;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Autowired
    private SessionDisconnectCleanupScheduler cleanupScheduler;

    @Autowired
    private MatchHistoryRepository matchHistoryRepository;

    @Test
    void finishedSession_archivedAfterGracePeriod() throws Exception {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        UUID sessionId = lobbyService.joinLobby(user1, "Archive1").sessionId();
        lobbyService.joinLobby(user2, "Archive2");
        CompetitionTestFixtures.advanceToAuctioning(lobbyService, sessionStore, sessionId, user1, user2);
        CompetitionTestFixtures.completeDuelDraft(auctionService, cardCatalog, sessionStore, sessionId, user1, user2);

        await().atMost(10, SECONDS).until(() ->
            sessionStore.getById(sessionId).getStatus() == SessionStatus.FINISHED
        );

        clearFinishedObservation(sessionId);
        backdateFinishedObservation(sessionId, Instant.now().minusSeconds(300));
        cleanupScheduler.archiveFinishedSessions();

        assertThat(sessionStore.findById(sessionId)).isEmpty();
        assertThat(matchHistoryRepository.findBySessionId(sessionId)).isPresent();
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<UUID, Instant> finishedObservedAtMap() throws Exception {
        Field field = SessionDisconnectCleanupScheduler.class.getDeclaredField("finishedObservedAt");
        field.setAccessible(true);
        return (ConcurrentHashMap<UUID, Instant>) field.get(cleanupScheduler);
    }

    private void clearFinishedObservation(UUID sessionId) throws Exception {
        finishedObservedAtMap().remove(sessionId);
    }

    private void backdateFinishedObservation(UUID sessionId, Instant observedAt) throws Exception {
        finishedObservedAtMap().put(sessionId, observedAt);
    }
}
