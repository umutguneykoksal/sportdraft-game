package com.example.auction.common.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.common.realtime.RealtimeConnectionAuthorizer;
import com.example.auction.common.realtime.SessionEventLog;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SessionEventSyncControllerTest {

    @Mock
    private SessionEventLog eventLog;
    @Mock
    private RealtimeConnectionAuthorizer connectionAuthorizer;

    private SessionEventSyncController controller;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new SessionEventSyncController(eventLog, connectionAuthorizer);
    }

    @Test
    void sync_returnsEventsAfterMembershipCheck() {
        List<Map<String, Object>> events = List.of(Map.of("seq", 2, "eventType", "bid_placed"));
        when(eventLog.since(sessionId, 1)).thenReturn(events);
        when(eventLog.currentSeq(sessionId)).thenReturn(2L);

        SessionEventSyncController.SyncResponse response =
            controller.sync(sessionId, userId.toString(), 1, false);

        verify(connectionAuthorizer).assertSessionMembership(sessionId, userId);
        assertThat(response.events()).isEqualTo(events);
        assertThat(response.lastSeq()).isEqualTo(2L);
    }

    @Test
    void sync_rejectsNonMember() {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is not part of this session"))
            .when(connectionAuthorizer)
            .assertSessionMembership(sessionId, userId);

        assertThatThrownBy(() -> controller.sync(sessionId, userId.toString(), 0, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            });

        verify(eventLog, never()).since(sessionId, 0);
    }

    @Test
    void sync_spectatorSkipsMembership() {
        when(eventLog.since(sessionId, 5)).thenReturn(List.of());
        when(eventLog.currentSeq(sessionId)).thenReturn(5L);

        SessionEventSyncController.SyncResponse response =
            controller.sync(sessionId, userId.toString(), 5, true);

        verify(connectionAuthorizer, never()).assertSessionMembership(sessionId, userId);
        assertThat(response.events()).isEmpty();
        assertThat(response.lastSeq()).isEqualTo(5L);
    }
}
