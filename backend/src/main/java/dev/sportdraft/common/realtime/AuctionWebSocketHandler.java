package dev.sportdraft.common.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
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
    private final ObjectMapper objectMapper;
    private final Map<String, UUID> sessionIdByWsId = new ConcurrentHashMap<>();

    public AuctionWebSocketHandler(WebSocketSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID sessionId = registry.register(session);
        sessionIdByWsId.put(session.getId(), sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            if (node.has("type") && "ping".equals(node.get("type").asText())) {
                Map<String, Object> pong = new LinkedHashMap<>();
                pong.put("eventType", "pong");
                pong.put("seq", 0L);
                pong.put("occurredAt", Instant.now().toString());
                pong.put("payload", Map.of("now", Instant.now().toString()));
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
            }
        } catch (Exception ignored) {
            // Client mutations stay on REST; ignore unknown payloads.
        }
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
