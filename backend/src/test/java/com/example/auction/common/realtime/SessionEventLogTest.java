package com.example.auction.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SessionEventLogTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private ListOperations<String, String> listOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SessionEventLog eventLog;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForList()).thenReturn(listOps);
        eventLog = new SessionEventLog(redis, objectMapper, 100, 6);
        sessionId = UUID.randomUUID();
    }

    @Test
    void append_incrementsSeqAndStoresEvent() throws Exception {
        when(valueOps.increment("session-events:" + sessionId + ":seq")).thenReturn(1L, 2L);

        long firstSeq = eventLog.nextSeq(sessionId);
        long secondSeq = eventLog.nextSeq(sessionId);

        Map<String, Object> envelope = Map.of("seq", 2L, "eventType", "bid_placed");
        assertThat(eventLog.append(sessionId, envelope)).isTrue();

        assertThat(firstSeq).isEqualTo(1L);
        assertThat(secondSeq).isEqualTo(2L);
        verify(listOps).leftPush(eq("session-events:" + sessionId + ":log"), any(String.class));
        verify(listOps).trim("session-events:" + sessionId + ":log", 0, 99);
        verify(redis).expire(eq("session-events:" + sessionId + ":log"), any(Duration.class));
    }

    @Test
    void since_returnsEventsAfterSeqAscending() throws Exception {
        String newer = objectMapper.writeValueAsString(Map.of("seq", 3, "eventType", "round_open"));
        String older = objectMapper.writeValueAsString(Map.of("seq", 2, "eventType", "bid_placed"));
        when(listOps.range("session-events:" + sessionId + ":log", 0, 99)).thenReturn(List.of(newer, older));
        when(valueOps.get("session-events:" + sessionId + ":seq")).thenReturn("3");

        List<Map<String, Object>> events = eventLog.since(sessionId, 1);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("seq")).isEqualTo(2);
        assertThat(events.get(1).get("seq")).isEqualTo(3);
    }

    @Test
    void since_returnsEmptyWhenGapBeyondRing() {
        when(listOps.range("session-events:" + sessionId + ":log", 0, 99)).thenReturn(List.of());

        List<Map<String, Object>> events = eventLog.since(sessionId, 1);

        assertThat(events).isEmpty();
    }
}
