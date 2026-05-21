package com.example.auction.lobby.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.common.realtime.SessionEventLog;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
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
            auctionService
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
