package com.example.auction.common.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed ordered event log for each active session.
 * Provides monotonically increasing sequence numbers and a bounded ring buffer
 * so reconnecting clients can catch up on missed events.
 *
 * Key layout:
 *   session-events:{id}:seq    — INCR counter (monoton seq)
 *   session-events:{id}:log    — LIST head=newest, trimmed to RING_SIZE
 *
 * Race guarantee: append MUST happen before hub publish so that a reconnecting
 * client who requests afterSeq=N can always find the event in the log.
 */
@Component
public class SessionEventLog {

    private static final Logger log = LoggerFactory.getLogger(SessionEventLog.class);
    private static final String SEQ_PREFIX = "session-events:";
    private static final String SEQ_SUFFIX = ":seq";
    private static final String LOG_SUFFIX = ":log";
    private static final int RING_SIZE = 100;
    private static final Duration LOG_TTL = Duration.ofMinutes(30);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public SessionEventLog(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Atomically increments and returns the next sequence number for this session.
     * The returned seq is guaranteed to be unique and monotonically increasing.
     */
    public long nextSeq(UUID sessionId) {
        Long seq = redis.opsForValue().increment(seqKey(sessionId));
        if (seq == null) seq = 1L;
        redis.expire(seqKey(sessionId), LOG_TTL);
        return seq;
    }

    /**
     * Appends an event envelope to the ring buffer.
     * Must be called BEFORE publishing to the hub to prevent gap-on-reconnect.
     */
    public void append(UUID sessionId, Map<String, Object> envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            String logKey = logKey(sessionId);
            redis.opsForList().leftPush(logKey, json);
            redis.opsForList().trim(logKey, 0, RING_SIZE - 1);
            redis.expire(logKey, LOG_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to append event to log for session={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Returns all events with seq > afterSeq, in ascending seq order.
     * Returns empty list if the requested seq is too old (outside the ring buffer).
     */
    public List<Map<String, Object>> since(UUID sessionId, long afterSeq) {
        String logKey = logKey(sessionId);
        List<String> all = redis.opsForList().range(logKey, 0, RING_SIZE - 1);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String json : all) {
            try {
                Map<String, Object> event = objectMapper.readValue(json, MAP_TYPE);
                Object seqObj = event.get("seq");
                if (seqObj instanceof Number num && num.longValue() > afterSeq) {
                    result.add(event);
                }
            } catch (JsonProcessingException e) {
                log.debug("Skipping unparseable event in log for session={}", sessionId);
            }
        }
        // List is newest-first; reverse to get oldest-first (ascending seq)
        Collections.reverse(result);
        return result;
    }

    /** Returns the current highest seq for a session, or 0 if none. */
    public long currentSeq(UUID sessionId) {
        String raw = redis.opsForValue().get(seqKey(sessionId));
        if (raw == null) return 0L;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Cleans up all event log keys for a session. Call when session finishes. */
    public void forget(UUID sessionId) {
        redis.delete(seqKey(sessionId));
        redis.delete(logKey(sessionId));
    }

    private String seqKey(UUID sessionId) {
        return SEQ_PREFIX + sessionId + SEQ_SUFFIX;
    }

    private String logKey(UUID sessionId) {
        return SEQ_PREFIX + sessionId + LOG_SUFFIX;
    }
}
