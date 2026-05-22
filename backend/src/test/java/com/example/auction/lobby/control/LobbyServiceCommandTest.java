package com.example.auction.lobby.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    void selectFormation_happyPath_updatesPlayerFormation() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = formationSession(sessionId);
        when(sessionStore.getById(sessionId)).thenReturn(session);
        when(eventLog.currentSeq(sessionId)).thenReturn(1L);

        var response = lobbyService.selectFormation(sessionId, hostUserId, Formation.FOUR_THREE_THREE);

        assertThat(response.myFormation()).isEqualTo(Formation.FOUR_THREE_THREE);
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("formation_chosen"), any());
        verify(sessionStore).saveIfVersionMatch(eq(session), any(Long.class));
    }

    @Test
    void confirmFormationReady_firstPlayerBroadcastsReadyCount() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = formationSession(sessionId);
        when(sessionStore.getById(sessionId)).thenReturn(session);
        when(eventLog.currentSeq(sessionId)).thenReturn(1L);

        var response = lobbyService.confirmFormationReady(sessionId, hostUserId);

        assertThat(response.matchStartReadyCount()).isEqualTo(1);
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("formation_ready"), any());
        verify(sessionStore).saveIfVersionMatch(eq(session), any(Long.class));
    }

    @Test
    void confirmFormationReady_bothReadyStartsCountdown() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = formationSession(sessionId);
        session.registerMatchStartReady(hostUserId);
        when(sessionStore.getById(sessionId)).thenReturn(session);
        when(eventLog.currentSeq(sessionId)).thenReturn(1L);

        var response = lobbyService.confirmFormationReady(sessionId, guestUserId);

        assertThat(response.matchStartReadyCount()).isEqualTo(2);
        assertThat(session.getCountdownEndsAt()).isNotNull();
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("formation_ready"), any());
    }

    @Test
    void confirmFormationReady_idempotentWhenAlreadyReady() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = formationSession(sessionId);
        session.registerMatchStartReady(hostUserId);
        when(sessionStore.getById(sessionId)).thenReturn(session);
        when(eventLog.currentSeq(sessionId)).thenReturn(1L);

        lobbyService.confirmFormationReady(sessionId, hostUserId);

        verify(sessionStore, never()).saveIfVersionMatch(any(), any(Long.class));
    }

    @Test
    void getSnapshot_returnsLobbyView() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = waitingSession(sessionId, hostUserId);
        when(sessionStore.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        when(eventLog.currentSeq(sessionId)).thenReturn(3L);

        var response = lobbyService.getSnapshot(sessionId, hostUserId);

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.joined()).isTrue();
        assertThat(response.lastSeq()).isEqualTo(3L);
    }

    @Test
    void advanceTimedPhases_countdownExpired_movesToFormationSelect() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = waitingSession(sessionId, hostUserId);
        session.getPlayers().add(new ActivePlayer(
            UUID.randomUUID(), guestUserId, "Guest", Formation.FOUR_FOUR_TWO, 100
        ));
        session.moveToCountdown(Instant.now().minusSeconds(1));
        when(sessionStore.findByStatus(SessionStatus.COUNTDOWN)).thenReturn(List.of(session));
        when(sessionStore.findByStatus(SessionStatus.FORMATION_SELECT)).thenReturn(List.of());

        lobbyService.advanceTimedPhases();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.FORMATION_SELECT);
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("formation_phase_started"), any());
    }

    @Test
    void advanceTimedPhases_formationReadyExpired_startsAuction() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = formationSession(sessionId);
        session.registerMatchStartReady(hostUserId);
        session.registerMatchStartReady(guestUserId);
        session.setCountdownEndsAt(Instant.now().minusSeconds(1));
        UUID cardId = UUID.randomUUID();
        when(sessionStore.findByStatus(SessionStatus.COUNTDOWN)).thenReturn(List.of());
        when(sessionStore.findByStatus(SessionStatus.FORMATION_SELECT)).thenReturn(List.of(session));
        when(cardCatalog.getCardIdsForPool(session.getPoolLeague(), session.getMinPoolRating()))
            .thenReturn(List.of(cardId));

        lobbyService.advanceTimedPhases();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.AUCTIONING);
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("auctioning_started"), any());
        verify(auctionService).ensureOpenRound(sessionId);
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
        session.getPlayers().add(new ActivePlayer(
            UUID.randomUUID(), hostId, "Host", Formation.FOUR_FOUR_TWO, 100
        ));
        session.addToTurnOrder(hostId);
        return session;
    }

    private ActiveSession formationSession(UUID sessionId) {
        ActiveSession session = waitingSession(sessionId, hostUserId);
        session.getPlayers().add(new ActivePlayer(
            UUID.randomUUID(), guestUserId, "Guest", Formation.FOUR_FOUR_TWO, 100
        ));
        session.addToTurnOrder(guestUserId);
        session.moveToFormationSelect(Instant.now().plus(30, ChronoUnit.SECONDS));
        return session;
    }
}
