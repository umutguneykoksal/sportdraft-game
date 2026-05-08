package com.example.auction.common.realtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RealtimeEnvelopeFactory {

    public Map<String, Object> create(UUID sessionId, String eventType, Object payload, long version) {
        return Map.of(
            "eventId", UUID.randomUUID().toString(),
            "sessionId", sessionId,
            "eventType", eventType,
            "version", version,
            "occurredAt", Instant.now().toString(),
            "payload", payload
        );
    }
}
