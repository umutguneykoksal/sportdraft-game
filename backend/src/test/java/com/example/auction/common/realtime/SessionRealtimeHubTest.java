package com.example.auction.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.WebSocketSession;

class SessionRealtimeHubTest {

    private SessionRealtimeHub hub;
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        hub = new SessionRealtimeHub(new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void registerSse_tracksSessionAndClearsDisconnectMarker() {
        SseEmitter emitter = new SseEmitter(60_000L);

        hub.registerSse(sessionId, emitter);

        assertThat(hub.sessionIds()).containsExactly(sessionId);
        assertThat(hub.hasActiveConnections(sessionId)).isTrue();
    }

    @Test
    void registerWs_tracksOpenWebsocketSessions() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.isOpen()).thenReturn(true);

        hub.registerWs(sessionId, ws);

        assertThat(hub.hasActiveConnections(sessionId)).isTrue();
        assertThat(hub.getOpenWsSessions(sessionId)).containsExactly(ws);
    }

    @Test
    void removeSse_marksDisconnectedWhenLastConnectionRemoved() {
        SseEmitter emitter = new SseEmitter(60_000L);
        hub.registerSse(sessionId, emitter);

        hub.removeSse(sessionId, emitter);

        assertThat(hub.hasActiveConnections(sessionId)).isFalse();
        assertThat(hub.isDisconnectedLongerThan(sessionId, Duration.ofMillis(-1))).isTrue();
    }

    @Test
    void forgetSession_clearsDisconnectTracking() {
        SseEmitter emitter = new SseEmitter(60_000L);
        hub.registerSse(sessionId, emitter);
        hub.removeSse(sessionId, emitter);

        hub.forgetSession(sessionId);

        assertThat(hub.isDisconnectedLongerThan(sessionId, Duration.ofHours(1))).isFalse();
    }

    @Test
    void publish_sendsEnvelopeToRegisteredSseEmitter() throws Exception {
        SseEmitter emitter = new SseEmitter(60_000L);
        hub.registerSse(sessionId, emitter);
        Map<String, Object> envelope = Map.of("eventType", "round_started", "eventId", 1L, "payload", Map.of("x", 1));

        hub.publish(sessionId, envelope);

        assertThat(hub.hasActiveConnections(sessionId)).isTrue();
    }

    @Test
    void publish_removesClosedWebsocketSession() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.isOpen()).thenReturn(false);
        hub.registerWs(sessionId, ws);

        hub.publish(sessionId, Map.of("eventType", "ping", "eventId", 2L));

        assertThat(hub.getOpenWsSessions(sessionId)).isEmpty();
    }

    @Test
    void isDisconnectedLongerThan_returnsFalseWhileConnectionsActive() {
        SseEmitter emitter = new SseEmitter(60_000L);
        hub.registerSse(sessionId, emitter);

        assertThat(hub.isDisconnectedLongerThan(sessionId, Duration.ofHours(1))).isFalse();
    }

    @Test
    void sessionIds_includesBothSseAndWsSessions() throws Exception {
        SseEmitter emitter = new SseEmitter(60_000L);
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.isOpen()).thenReturn(true);
        UUID otherSession = UUID.randomUUID();

        hub.registerSse(sessionId, emitter);
        hub.registerWs(otherSession, ws);

        assertThat(hub.sessionIds()).containsExactlyInAnyOrder(sessionId, otherSession);
    }
}
