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
