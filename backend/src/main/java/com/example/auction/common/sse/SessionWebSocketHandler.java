package com.example.auction.common.sse;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SessionWebSocketHandler extends TextWebSocketHandler {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final Map<String, UUID> sessionIdByWsId = new ConcurrentHashMap<>();

    public SessionWebSocketHandler(SseEmitterRegistry sseEmitterRegistry) {
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        String token = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
        if (!isValidToken(token)) {
            session.close(new CloseStatus(HttpStatus.UNAUTHORIZED.value(), "Invalid token"));
            return;
        }

        String path = uri.getPath();
        String[] parts = path.split("/");
        if (parts.length == 0) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        String sessionIdRaw = parts[parts.length - 1];
        UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionIdRaw);
        } catch (IllegalArgumentException ex) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        sseEmitterRegistry.registerWebSocket(sessionId, session);
        sessionIdByWsId.put(session.getId(), sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID sessionId = sessionIdByWsId.remove(session.getId());
        if (sessionId != null) {
            sseEmitterRegistry.removeWebSocket(sessionId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        UUID sessionId = sessionIdByWsId.remove(session.getId());
        if (sessionId != null) {
            sseEmitterRegistry.removeWebSocket(sessionId, session);
        }
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private boolean isValidToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(token);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
