package com.example.auction.lobby.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.common.realtime.SessionEventLog;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.PoolLeague;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LobbyServiceCommandTest {

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

    private final UUID hostUserId = UUID.randomUUID();
    private final UUID guestUserId = UUID.randomUUID();

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
    void createRoom_happyPath_persistsWaitingSession() {
        when(sessionStore.findAllActiveIds()).thenReturn(List.of());
        when(cardCatalog.countCardsForPool(PoolLeague.PREMIER_LEAGUE, 70)).thenReturn(100);

        var response = lobbyService.createRoom(hostUserId, "Host", GameMode.DUEL);

        assertThat(response.status()).isEqualTo(SessionStatus.WAITING);
        assertThat(response.playerCount()).isEqualTo(1);
        ArgumentCaptor<ActiveSession> captor = ArgumentCaptor.forClass(ActiveSession.class);
        verify(sessionStore).save(captor.capture());
        assertThat(captor.getValue().getPlayers()).hasSize(1);
    }

    @Test
    void createRoom_maxConcurrentRooms_throws409() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(UUID.randomUUID());
        }
        when(sessionStore.findAllActiveIds()).thenReturn(ids);

        assertThatThrownBy(() -> lobbyService.createRoom(hostUserId, "Host", GameMode.DUEL))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
    }

    @Test
    void joinRoom_happyPath_addsSecondPlayer() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = waitingSession(sessionId, hostUserId);
        when(sessionStore.getById(sessionId)).thenReturn(session);

        var response = lobbyService.joinRoom(sessionId, guestUserId, "Guest");

        assertThat(response.playerCount()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(SessionStatus.COUNTDOWN);
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("countdown_started"), any());
    }

    @Test
    void joinRoom_roomFull_throws409() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = waitingSession(sessionId, hostUserId);
        session.getPlayers().add(new com.example.auction.session.redis.ActivePlayer(
            UUID.randomUUID(), guestUserId, "Guest", Formation.FOUR_FOUR_TWO, 100
        ));
        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> lobbyService.joinRoom(sessionId, UUID.randomUUID(), "Late"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Room is full");
            });
    }

    @Test
    void joinRoom_wrongPhase_throws409() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = waitingSession(sessionId, hostUserId);
        session.moveToCountdown(Instant.now().plusSeconds(5));
        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> lobbyService.joinRoom(sessionId, guestUserId, "Guest"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Room is no longer accepting players");
            });
    }

    @Test
    void selectFormation_wrongPhase_throws409() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = waitingSession(sessionId, hostUserId);
        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> lobbyService.selectFormation(sessionId, hostUserId, Formation.FOUR_THREE_THREE))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
    }

    @Test
    void leaveLobby_emptyRoom_deletesSession() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = waitingSession(sessionId, hostUserId);
        when(sessionStore.getById(sessionId)).thenReturn(session);

        lobbyService.leaveLobby(sessionId, hostUserId);

        verify(sessionStore).delete(sessionId);
        verify(sseEmitterRegistry).forgetSession(sessionId);
        verify(eventLog).forget(sessionId);
    }

    private ActiveSession waitingSession(UUID sessionId, UUID hostId) {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.WAITING, Instant.now(), GameMode.DUEL);
        session.getPlayers().add(new com.example.auction.session.redis.ActivePlayer(
            UUID.randomUUID(), hostId, "Host", Formation.FOUR_FOUR_TWO, 100
        ));
        session.addToTurnOrder(hostId);
        return session;
    }
}
