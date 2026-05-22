package dev.sportdraft.common.realtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RealtimeEnvelopeFactory {

    /**
     * Creates the canonical event envelope sent over both SSE and WebSocket.
     * seq is the session-scoped monoton sequence number; 0 means unsequenced (e.g. heartbeat).
     */
    public Map<String, Object> create(UUID sessionId, String eventType, Object payload, long seq) {
        // LinkedHashMap preserves insertion order for readable JSON.
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", UUID.randomUUID().toString());
        env.put("sessionId", sessionId);
        env.put("eventType", eventType);
        env.put("seq", seq);
        env.put("occurredAt", Instant.now().toString());
        env.put("payload", payload);
        return env;
    }
}
