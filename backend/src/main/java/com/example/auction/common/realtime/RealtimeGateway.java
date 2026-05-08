package com.example.auction.common.realtime;

import java.util.UUID;

public interface RealtimeGateway {
    void publish(UUID sessionId, String eventType, Object payload);
}
