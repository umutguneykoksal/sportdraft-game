package dev.sportdraft.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionRegistryTest {

    @Mock
    private SessionRealtimeHub sessionRealtimeHub;
    @Mock
    private RealtimeConnectionAuthorizer realtimeConnectionAuthorizer;
    @Mock
    private RealtimeGateway realtimeGateway;
    @Mock
    private RealtimeConnectionTracker connectionTracker;
    @Mock
    private RealtimePresenceChecker presenceChecker;
    @Mock
    private WebSocketSession playerSession;
    @Mock
    private WebSocketSession spectatorSession;

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry(
            sessionRealtimeHub,
            realtimeConnectionAuthorizer,
            realtimeGateway,
            connectionTracker,
            presenceChecker
        );
    }

    @Test
    void removeDoesNotForfeitWhenPlayerStillPresent() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        stubSession(playerSession, "ws-player", sessionId, userId, false);
        when(presenceChecker.isPlayerPresent(sessionId, userId)).thenReturn(true);
        when(sessionRealtimeHub.getOpenWsSessions(sessionId)).thenReturn(List.of());

        registry.register(playerSession);
        registry.remove(sessionId, playerSession);

        verify(connectionTracker, never()).onPlayerFullyDisconnected(eq(sessionId), eq(userId));
    }

    @Test
    void removeForfeitsWhenNoPresenceRemains() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        stubSession(playerSession, "ws-player", sessionId, userId, false);
        when(presenceChecker.isPlayerPresent(sessionId, userId)).thenReturn(false);
        when(sessionRealtimeHub.getOpenWsSessions(sessionId)).thenReturn(List.of());

        registry.register(playerSession);
        registry.remove(sessionId, playerSession);

        verify(connectionTracker).onPlayerFullyDisconnected(sessionId, userId);
    }

    @Test
    void spectatorRemoveNeverTriggersForfeit() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        stubSession(spectatorSession, "ws-spec", sessionId, userId, true);

        registry.register(spectatorSession);
        registry.remove(sessionId, spectatorSession);

        verify(connectionTracker, never()).onPlayerFullyDisconnected(eq(sessionId), eq(userId));
        verify(presenceChecker, never()).isPlayerPresent(sessionId, userId);
    }

    @Test
    void register_rejectsInvalidToken() {
        UUID sessionId = UUID.randomUUID();
        URI uri = URI.create("ws://localhost/ws/sessions?sessionId=" + sessionId + "&token=not-a-uuid");
        when(playerSession.getUri()).thenReturn(uri);

        assertThatThrownBy(() -> registry.register(playerSession))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(rse.getReason()).isEqualTo("Missing or invalid token");
            });
    }

    @Test
    void hasNonSpectatorPlayerConnectionIgnoresSpectators() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        stubSession(playerSession, "ws-player", sessionId, userId, false);
        stubSession(spectatorSession, "ws-spec", sessionId, userId, true);
        when(sessionRealtimeHub.getOpenWsSessions(sessionId)).thenReturn(List.of());

        registry.register(playerSession);
        registry.register(spectatorSession);

        assertThat(registry.hasNonSpectatorPlayerConnection(sessionId, userId)).isTrue();
    }

    private void stubSession(WebSocketSession ws, String wsId, UUID sessionId, UUID userId, boolean spectator) {
        URI uri = URI.create(
            "ws://localhost/ws/sessions?sessionId=" + sessionId
                + "&token=" + userId
                + (spectator ? "&spectator=true" : "")
        );
        when(ws.getUri()).thenReturn(uri);
        when(ws.getId()).thenReturn(wsId);
    }
}
