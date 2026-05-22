package dev.sportdraft.lobby.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.bidding.control.CardCatalogService;
import dev.sportdraft.common.realtime.SessionEventLog;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LobbyServiceListRoomsTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private CardCatalogService cardCatalog;
    @Mock
    private SessionEventLog eventLog;
    @Mock
    private AuctionService auctionService;

    private LobbyService lobbyService;

    @BeforeEach
    void setUp() {
        lobbyService = new LobbyService(
            sessionStore,
            sseEmitterRegistry,
            cardCatalog,
            eventLog,
            auctionService,
            LobbyServiceTestSupport.passthroughMutationGuard()
        );
    }

    @Test
    void listRooms_hidesPostDraftTournamentSessions() {
        UUID waitingId = UUID.randomUUID();
        UUID awaitingResultId = UUID.randomUUID();
        when(sessionStore.findAllActiveIds()).thenReturn(List.of(waitingId, awaitingResultId));
        when(sessionStore.findByStatusNot(SessionStatus.FINISHED)).thenReturn(List.of(
            session(waitingId, SessionStatus.WAITING),
            session(awaitingResultId, SessionStatus.AWAITING_RESULT)
        ));
        when(sseEmitterRegistry.spectatorCount(waitingId)).thenReturn(0);

        var rooms = lobbyService.listRooms();

        assertThat(rooms).hasSize(1);
        assertThat(rooms.getFirst().sessionId()).isEqualTo(waitingId);
    }

    private static ActiveSession session(UUID id, SessionStatus status) {
        return new ActiveSession(id, status, Instant.now(), GameMode.TOURNAMENT);
    }
}
