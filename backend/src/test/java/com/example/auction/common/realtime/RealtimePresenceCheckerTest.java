package com.example.auction.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.auction.common.sse.SseEmitterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimePresenceCheckerTest {

    @Mock
    private RealtimeConnectionTracker connectionTracker;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private WebSocketSessionRegistry webSocketSessionRegistry;
    @InjectMocks
    private RealtimePresenceChecker presenceChecker;

    @Test
    void presentWhenRecentPing() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(connectionTracker.hasRecentPing(sessionId, userId)).thenReturn(true);

        assertThat(presenceChecker.isPlayerPresent(sessionId, userId)).isTrue();
    }

    @Test
    void presentWhenSseConnected() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(connectionTracker.hasRecentPing(sessionId, userId)).thenReturn(false);
        when(sseEmitterRegistry.hasNonSpectatorPlayerConnection(sessionId, userId)).thenReturn(true);

        assertThat(presenceChecker.isPlayerPresent(sessionId, userId)).isTrue();
    }

    @Test
    void presentWhenWsConnected() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(connectionTracker.hasRecentPing(sessionId, userId)).thenReturn(false);
        when(sseEmitterRegistry.hasNonSpectatorPlayerConnection(sessionId, userId)).thenReturn(false);
        when(webSocketSessionRegistry.hasNonSpectatorPlayerConnection(sessionId, userId)).thenReturn(true);

        assertThat(presenceChecker.isPlayerPresent(sessionId, userId)).isTrue();
    }

    @Test
    void absentWhenNoChannel() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(connectionTracker.hasRecentPing(sessionId, userId)).thenReturn(false);
        when(sseEmitterRegistry.hasNonSpectatorPlayerConnection(sessionId, userId)).thenReturn(false);
        when(webSocketSessionRegistry.hasNonSpectatorPlayerConnection(sessionId, userId)).thenReturn(false);

        assertThat(presenceChecker.isPlayerPresent(sessionId, userId)).isFalse();
    }
}
