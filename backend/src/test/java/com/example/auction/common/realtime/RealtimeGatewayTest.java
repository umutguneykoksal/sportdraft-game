package com.example.auction.common.realtime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeGatewayTest {

    @Mock
    private SessionRealtimeHub sessionRealtimeHub;
    @Mock
    private RealtimeEnvelopeFactory realtimeEnvelopeFactory;
    @InjectMocks
    private DefaultRealtimeGateway realtimeGateway;

    @Test
    void publishUsesEnvelopeFactoryAndHub() {
        UUID sessionId = UUID.randomUUID();
        Map<String, Object> envelope = Map.of("eventType", "round_started");
        when(realtimeEnvelopeFactory.create(sessionId, "round_started", Map.of("x", 1), 1L)).thenReturn(envelope);

        realtimeGateway.publish(sessionId, "round_started", Map.of("x", 1));

        verify(sessionRealtimeHub).publish(sessionId, envelope);
    }
}
