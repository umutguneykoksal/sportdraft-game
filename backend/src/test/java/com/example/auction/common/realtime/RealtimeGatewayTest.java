package com.example.auction.common.realtime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private SessionEventLog eventLog;
    @InjectMocks
    private DefaultRealtimeGateway realtimeGateway;

    @Test
    void publishSequencesAndDelegatesToHub() {
        UUID sessionId = UUID.randomUUID();
        Map<String, Object> envelope = Map.of("eventType", "round_started");
        when(eventLog.nextSeq(sessionId)).thenReturn(1L);
        when(realtimeEnvelopeFactory.create(eq(sessionId), eq("round_started"), eq(Map.of("x", 1)), anyLong()))
            .thenReturn(envelope);

        realtimeGateway.publish(sessionId, "round_started", Map.of("x", 1));

        verify(eventLog).append(sessionId, envelope);
        verify(sessionRealtimeHub).publish(sessionId, envelope);
    }
}
