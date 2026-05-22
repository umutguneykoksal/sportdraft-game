package com.example.auction.common.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.common.realtime.RealtimeConnectionTracker;
import com.example.auction.common.realtime.RealtimeGateway;
import com.example.auction.common.realtime.RealtimePresenceChecker;
import com.example.auction.common.realtime.SessionRealtimeHub;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseEmitterRegistryTest {

    @Mock
    private RealtimeGateway realtimeGateway;
    @Mock
    private SessionRealtimeHub sessionRealtimeHub;
    @Mock
    private RealtimeConnectionTracker connectionTracker;
    @Mock
    private RealtimePresenceChecker presenceChecker;

    private SseEmitterRegistry registry;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry(
            realtimeGateway,
            sessionRealtimeHub,
            connectionTracker,
            presenceChecker
        );
    }

    @Test
    void registerPlayerTracksConnectionAndBroadcastsViaGateway() {
        SseEmitter emitter = registry.register(sessionId, false, userId);

        assertThat(emitter).isNotNull();
        verify(sessionRealtimeHub).registerSse(sessionId, emitter);
        verify(connectionTracker).onPlayerConnected(sessionId, userId);
    }

    @Test
    void registerSpectatorIncrementsSpectatorCount() {
        registry.register(sessionId, true, null);
        SseEmitter playerEmitter = registry.register(sessionId, false, userId);

        assertThat(registry.spectatorCount(sessionId)).isEqualTo(1);
        assertThat(registry.hasNonSpectatorPlayerConnection(sessionId, userId)).isTrue();
        assertThat(playerEmitter).isNotNull();
    }

    @Test
    void registerReplacesExistingPlayerEmitter() {
        SseEmitter first = registry.register(sessionId, false, userId);
        SseEmitter second = registry.register(sessionId, false, userId);

        assertThat(second).isNotSameAs(first);
        assertThat(registry.hasNonSpectatorPlayerConnection(sessionId, userId)).isTrue();
    }

    @Test
    void broadcastDelegatesToRealtimeGateway() {
        Map<String, Object> payload = Map.of("x", 1);

        registry.broadcast(sessionId, "round_started", payload);

        verify(realtimeGateway).publish(sessionId, "round_started", payload);
    }

    @Test
    void sessionIdsDelegatesToHub() {
        when(sessionRealtimeHub.sessionIds()).thenReturn(List.of(sessionId));

        assertThat(registry.sessionIds()).containsExactly(sessionId);
    }

    @Test
    void hasActiveConnectionsDelegatesToHub() {
        when(sessionRealtimeHub.hasActiveConnections(sessionId)).thenReturn(true);

        assertThat(registry.hasActiveConnections(sessionId)).isTrue();
    }

    @Test
    void hasActivePlayerEmitterUsesPresenceChecker() {
        when(presenceChecker.isPlayerPresent(sessionId, userId)).thenReturn(true);

        assertThat(registry.hasActivePlayerEmitter(sessionId, userId)).isTrue();
    }

    @Test
    void isPlayerDisconnectedLongerThanClearsTrackerWhenPresent() {
        when(presenceChecker.isPlayerPresent(sessionId, userId)).thenReturn(true);

        assertThat(registry.isPlayerDisconnectedLongerThan(sessionId, userId, Duration.ofSeconds(30)))
            .isFalse();
        verify(connectionTracker).forgetPlayerDisconnect(sessionId, userId);
    }

    @Test
    void isPlayerDisconnectedLongerThanUsesTrackerWhenAbsent() {
        when(presenceChecker.isPlayerPresent(sessionId, userId)).thenReturn(false);
        when(connectionTracker.isPlayerDisconnectedLongerThan(sessionId, userId, Duration.ofSeconds(30)))
            .thenReturn(true);

        assertThat(registry.isPlayerDisconnectedLongerThan(sessionId, userId, Duration.ofSeconds(30)))
            .isTrue();
    }

    @Test
    void isDisconnectedLongerThanUsesTrackerWhenNoConnections() {
        when(sessionRealtimeHub.hasActiveConnections(sessionId)).thenReturn(false);
        when(connectionTracker.isSessionDisconnectedLongerThan(sessionId, Duration.ofMinutes(2)))
            .thenReturn(true);

        assertThat(registry.isDisconnectedLongerThan(sessionId, Duration.ofMinutes(2))).isTrue();
    }

    @Test
    void recordPlayerPingDelegatesToTracker() {
        registry.recordPlayerPing(sessionId, userId);

        verify(connectionTracker).recordPing(sessionId, userId);
    }

    @Test
    void forgetSessionClearsTrackerAndHub() {
        registry.forgetSession(sessionId);

        verify(connectionTracker).forgetSession(sessionId);
        verify(sessionRealtimeHub).forgetSession(sessionId);
    }

    @Test
    void getPlayerDisconnectedAtDelegatesToTracker() {
        Instant disconnectedAt = Instant.now().minusSeconds(10);
        when(connectionTracker.getPlayerDisconnectedAt(sessionId, userId)).thenReturn(disconnectedAt);

        assertThat(registry.getPlayerDisconnectedAt(sessionId, userId)).isEqualTo(disconnectedAt);
    }

    @Test
    void forgetPlayerDisconnectDelegatesToTracker() {
        registry.forgetPlayerDisconnect(sessionId, userId);

        verify(connectionTracker).forgetPlayerDisconnect(sessionId, userId);
    }
}
