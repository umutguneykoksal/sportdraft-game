package com.example.auction.session.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.common.realtime.SessionEventLog;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionDisconnectCleanupSchedulerTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private SessionArchiver sessionArchiver;
    @Mock
    private SessionEventLog eventLog;

    private SessionDisconnectCleanupScheduler scheduler;
    private UUID sessionId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        scheduler = new SessionDisconnectCleanupScheduler(
            sessionStore,
            sseEmitterRegistry,
            sessionArchiver,
            eventLog,
            60,
            240,
            240
        );
        sessionId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void cleanupDisconnectedSessions_deletesEmptyTournamentWaitingLobbyAfter240Seconds() {
        ActiveSession session = tournamentWaitingSession();
        when(sessionStore.findAllActiveIds()).thenReturn(List.of(sessionId));
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));
        when(sseEmitterRegistry.hasActivePlayerEmitter(sessionId, userId)).thenReturn(false);
        when(sseEmitterRegistry.isPlayerDisconnectedLongerThan(
            eq(sessionId), eq(userId), eq(Duration.ofSeconds(240))
        )).thenReturn(true);

        scheduler.cleanupDisconnectedSessions();

        verify(sessionStore).delete(sessionId);
        verify(sseEmitterRegistry).forgetSession(sessionId);
        verify(eventLog).forget(sessionId);
        verify(sessionArchiver, never()).archive(any());
    }

    @Test
    void cleanupDisconnectedSessions_doesNotDeleteTournamentDuringSimulation() {
        ActiveSession session = tournamentWaitingSession();
        session.moveToAwaitingResult();
        when(sessionStore.findAllActiveIds()).thenReturn(List.of(sessionId));
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));

        scheduler.cleanupDisconnectedSessions();

        verify(sessionStore, never()).delete(sessionId);
        verify(sessionStore, never()).saveIfVersionMatch(any(), any(Long.class));
        verify(sseEmitterRegistry, never()).forgetSession(sessionId);
    }

    @Test
    void finishedGracePeriod_uses240SecondsForTournament() {
        ActiveSession session = tournamentWaitingSession();
        session.moveToFinished();

        assertThat(SessionDisconnectPolicy.finishedGracePeriod(session, Duration.ofSeconds(240)))
            .isEqualTo(Duration.ofSeconds(240));
    }

    @Test
    void cleanupDisconnectedSessions_awardsForfeitWhenOnePlayerDisconnected() {
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();
        ActiveSession session = duelAuctionSession(winnerId, loserId);

        when(sessionStore.findAllActiveIds()).thenReturn(List.of(sessionId));
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));
        when(sseEmitterRegistry.hasActivePlayerEmitter(sessionId, winnerId)).thenReturn(true);
        when(sseEmitterRegistry.hasActivePlayerEmitter(sessionId, loserId)).thenReturn(false);
        when(sseEmitterRegistry.isPlayerDisconnectedLongerThan(
            eq(sessionId), eq(loserId), eq(Duration.ofSeconds(60))
        )).thenReturn(true);

        scheduler.cleanupDisconnectedSessions();

        verify(sessionStore).saveIfVersionMatch(eq(session), any(Long.class));
        verify(sessionArchiver).archive(session);
        verify(sseEmitterRegistry).forgetSession(sessionId);
        verify(eventLog).forget(sessionId);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.FINISHED);
    }

    @Test
    void archiveFinishedSessions_archivesDuelAfterGracePeriod() throws Exception {
        ActiveSession session = duelAuctionSession(UUID.randomUUID(), UUID.randomUUID());
        session.moveToFinished();

        when(sessionStore.findByStatus(SessionStatus.FINISHED)).thenReturn(List.of(session));
        when(sessionStore.exists(sessionId)).thenReturn(true);

        scheduler.archiveFinishedSessions();
        backdateFinishedObservation(sessionId, Instant.now().minusSeconds(300));
        scheduler.archiveFinishedSessions();

        verify(sessionArchiver).archive(session);
        verify(sseEmitterRegistry).forgetSession(sessionId);
        verify(eventLog).forget(sessionId);
    }

    @SuppressWarnings("unchecked")
    private void backdateFinishedObservation(UUID sessionId, Instant observedAt) throws Exception {
        var field = SessionDisconnectCleanupScheduler.class.getDeclaredField("finishedObservedAt");
        field.setAccessible(true);
        var map = (java.util.concurrent.ConcurrentHashMap<UUID, Instant>) field.get(scheduler);
        map.put(sessionId, observedAt);
    }

    private ActiveSession duelAuctionSession(UUID user1, UUID user2) {
        ActiveSession session = new ActiveSession(
            sessionId,
            SessionStatus.AUCTIONING,
            Instant.now(),
            GameMode.DUEL
        );
        session.getPlayers().add(new ActivePlayer(
            UUID.randomUUID(), user1, "Home", Formation.FOUR_FOUR_TWO, 100L
        ));
        session.getPlayers().add(new ActivePlayer(
            UUID.randomUUID(), user2, "Away", Formation.FOUR_FOUR_TWO, 100L
        ));
        return session;
    }

    private ActiveSession tournamentWaitingSession() {
        ActiveSession session = new ActiveSession(
            sessionId,
            SessionStatus.WAITING,
            Instant.now().minusSeconds(3600),
            GameMode.TOURNAMENT
        );
        session.getPlayers().add(new ActivePlayer(
            UUID.randomUUID(),
            userId,
            "Player-1",
            Formation.FOUR_FOUR_TWO,
            100L
        ));
        return session;
    }
}
