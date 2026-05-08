package com.example.auction.common.realtime;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultRealtimeGateway implements RealtimeGateway {

    private final SessionRealtimeHub sessionRealtimeHub;
    private final RealtimeEnvelopeFactory envelopeFactory;

    public DefaultRealtimeGateway(SessionRealtimeHub sessionRealtimeHub, RealtimeEnvelopeFactory envelopeFactory) {
        this.sessionRealtimeHub = sessionRealtimeHub;
        this.envelopeFactory = envelopeFactory;
    }

    @Override
    public void publish(UUID sessionId, String eventType, Object payload) {
        sessionRealtimeHub.publish(sessionId, envelopeFactory.create(sessionId, eventType, payload, 1L));
    }
}
