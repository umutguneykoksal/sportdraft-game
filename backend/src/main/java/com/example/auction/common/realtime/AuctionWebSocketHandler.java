package com.example.auction.common.realtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AuctionWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry registry;
    private final Map<String, UUID> sessionIdByWsId = new ConcurrentHashMap<>();

    public AuctionWebSocketHandler(WebSocketSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID sessionId = registry.register(session);
        sessionIdByWsId.put(session.getId(), sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client messages are ignored for now; mutations stay on REST.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID sessionId = sessionIdByWsId.remove(session.getId());
        registry.remove(sessionId, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        UUID sessionId = sessionIdByWsId.remove(session.getId());
        registry.remove(sessionId, session);
    }
}
