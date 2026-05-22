package com.example.auction.session.redis;

import com.example.auction.session.entity.SessionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis-backed store for active game sessions.
 * Each session is stored as a single JSON document at key "session:{id}".
 *
 * Index layout:
 *   sessions:active              — SET of all non-FINISHED session IDs
 *   sessions:waiting             — ZSET scored by createdAt (for ordered matchmaking)
 *   sessions:status:{STATUS}     — SET per SessionStatus for targeted scheduler lookups
 */
@Component
public class ActiveSessionStore {

    private static final String KEY_PREFIX = "session:";
    private static final String INDEX_ACTIVE = "sessions:active";
    private static final String INDEX_WAITING = "sessions:waiting";
    private static final String STATUS_INDEX_PREFIX = "sessions:status:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private static final DefaultRedisScript<Long> SAVE_WITH_INDEXES_SCRIPT;

    static {
        // Lua: atomic SET + index update.
        // KEYS[1] = session key, KEYS[2] = new status index key
        // ARGV[1] = json, ARGV[2] = ttl seconds, ARGV[3] = session id string,
        // ARGV[4] = old status (may be empty), ARGV[5] = new status name
        String lua = """
            redis.call('SET', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[2]))
            local idStr = ARGV[3]
            local oldStatus = ARGV[4]
            local newStatus = ARGV[5]
            if newStatus == 'FINISHED' then
                redis.call('SREM', 'sessions:active', idStr)
                redis.call('ZREM', 'sessions:waiting', idStr)
            else
                redis.call('SADD', 'sessions:active', idStr)
                if newStatus == 'WAITING' then
                    redis.call('ZADD', 'sessions:waiting', tonumber(ARGV[6]), idStr)
                else
                    redis.call('ZREM', 'sessions:waiting', idStr)
                end
            end
            if oldStatus ~= '' and oldStatus ~= newStatus then
                redis.call('SREM', 'sessions:status:' .. oldStatus, idStr)
            end
            redis.call('SADD', 'sessions:status:' .. newStatus, idStr)
            return 1
            """;
        SAVE_WITH_INDEXES_SCRIPT = new DefaultRedisScript<>(lua, Long.class);
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ActiveSessionStore(StringRedisTemplate redis,
                              @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // --- CRUD ---

    public void save(ActiveSession session) {
        SessionStatus oldStatus = readCurrentStatus(session.getId());
        session.setVersion(session.getVersion() + 1);
        String json = toJson(session);
        runSaveScript(session, oldStatus, json);
    }

    /**
     * Optimistic save: uses WATCH/MULTI to ensure no concurrent modification.
     * Throws OptimisticLockException if the version in Redis has changed
     * since the session was read.
     */
    public void saveIfVersionMatch(ActiveSession session, long expectedVersion) {
        String key = key(session.getId());

        // Read current status before the WATCH/MULTI so we know the old index to clean up.
        SessionStatus oldStatus = readCurrentStatus(session.getId());

        @SuppressWarnings("unchecked")
        List<Object> results = redis.execute(new SessionCallback<List<Object>>() {
            @Override
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                operations.watch(key);
                String current = (String) operations.opsForValue().get(key);
                if (current != null) {
                    ActiveSession existing = fromJson(current);
                    if (existing.getVersion() != expectedVersion) {
                        operations.unwatch();
                        return null;
                    }
                }
                session.setVersion(expectedVersion + 1);
                operations.multi();
                operations.opsForValue().set(key, toJson(session), SESSION_TTL);
                return operations.exec();
            }
        });

        if (results == null || results.isEmpty()) {
            throw new OptimisticLockException(
                "Session " + session.getId() + " was modified concurrently (expected version " + expectedVersion + ")"
            );
        }
        updateIndexes(session, oldStatus);
    }

    public Optional<ActiveSession> findById(UUID sessionId) {
        String json = redis.opsForValue().get(key(sessionId));
        if (json == null) return Optional.empty();
        return Optional.of(fromJson(json));
    }

    public ActiveSession getById(UUID sessionId) {
        return findById(sessionId).orElseThrow(
            () -> new SessionNotFoundException("Active session not found: " + sessionId)
        );
    }

    public void delete(UUID sessionId) {
        SessionStatus oldStatus = readCurrentStatus(sessionId);
        redis.delete(key(sessionId));
        String idStr = sessionId.toString();
        redis.opsForSet().remove(INDEX_ACTIVE, idStr);
        redis.opsForZSet().remove(INDEX_WAITING, idStr);
        if (oldStatus != null) {
            redis.opsForSet().remove(STATUS_INDEX_PREFIX + oldStatus.name(), idStr);
        }
    }

    public boolean exists(UUID sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(key(sessionId)));
    }

    // --- Index-based queries ---

    /**
     * Returns all WAITING sessions sorted by createdAt (earliest first).
     * Uses the WAITING ZSET + MGET for a single round-trip batch fetch.
     */
    public List<ActiveSession> findWaitingSessions() {
        Set<String> ids = redis.opsForZSet().range(INDEX_WAITING, 0, -1);
        if (ids == null || ids.isEmpty()) return List.of();
        return multiGet(ids.stream().toList());
    }

    /**
     * Returns all active (non-FINISHED) session IDs.
     * Used by cleanup scheduler and admin operations.
     */
    public List<UUID> findAllActiveIds() {
        Set<String> ids = redis.opsForSet().members(INDEX_ACTIVE);
        if (ids == null) return List.of();
        return ids.stream().map(UUID::fromString).toList();
    }

    /**
     * Returns all active sessions matching a specific status.
     * Uses per-status secondary index + MGET (2 round trips total).
     */
    public List<ActiveSession> findByStatus(SessionStatus status) {
        Set<String> ids = redis.opsForSet().members(STATUS_INDEX_PREFIX + status.name());
        if (ids == null || ids.isEmpty()) return List.of();
        return multiGet(ids.stream().toList());
    }

    /**
     * Returns all active sessions NOT matching the given status.
     * Falls back to scanning the active set since there's no single index for "not X".
     */
    public List<ActiveSession> findByStatusNot(SessionStatus status) {
        Set<String> ids = redis.opsForSet().members(INDEX_ACTIVE);
        if (ids == null || ids.isEmpty()) return List.of();
        return multiGet(ids.stream().toList()).stream()
            .filter(s -> s.getStatus() != status)
            .toList();
    }

    public int deleteAllActive() {
        Set<String> ids = redis.opsForSet().members(INDEX_ACTIVE);
        if (ids == null || ids.isEmpty()) return 0;
        int count = ids.size();
        for (String idStr : ids) {
            redis.delete(KEY_PREFIX + idStr);
        }
        redis.delete(INDEX_ACTIVE);
        redis.delete(INDEX_WAITING);
        for (SessionStatus status : SessionStatus.values()) {
            redis.delete(STATUS_INDEX_PREFIX + status.name());
        }
        return count;
    }

    // --- Internal ---

    /**
     * Fetch multiple sessions in a single MGET round-trip, filtering out nulls / stale entries.
     */
    private List<ActiveSession> multiGet(List<String> idStrings) {
        List<String> keys = idStrings.stream()
            .map(id -> KEY_PREFIX + id)
            .toList();
        List<String> jsons = redis.opsForValue().multiGet(keys);
        if (jsons == null) return List.of();
        List<ActiveSession> result = new ArrayList<>(jsons.size());
        for (String json : jsons) {
            if (json != null) {
                result.add(fromJson(json));
            }
        }
        return result;
    }

    /**
     * Atomically saves the session JSON and updates all index SETs via Lua script.
     * Used by {@link #save(ActiveSession)}.
     */
    private void runSaveScript(ActiveSession session, SessionStatus oldStatus, String json) {
        String idStr = session.getId().toString();
        String oldStatusStr = oldStatus != null ? oldStatus.name() : "";
        String newStatusStr = session.getStatus().name();
        String createdAtScore = session.getCreatedAt() != null
            ? String.valueOf(session.getCreatedAt().toEpochMilli())
            : "0";
        redis.execute(
            SAVE_WITH_INDEXES_SCRIPT,
            List.of(key(session.getId()), STATUS_INDEX_PREFIX + newStatusStr),
            json,
            String.valueOf(SESSION_TTL.toSeconds()),
            idStr,
            oldStatusStr,
            newStatusStr,
            createdAtScore
        );
    }

    /**
     * Reads the current status of a session from Redis without full deserialization concern.
     * Returns null if session does not exist.
     */
    private SessionStatus readCurrentStatus(UUID sessionId) {
        String json = redis.opsForValue().get(key(sessionId));
        if (json == null) return null;
        return fromJson(json).getStatus();
    }

    private void updateIndexes(ActiveSession session, SessionStatus oldStatus) {
        String idStr = session.getId().toString();
        SessionStatus newStatus = session.getStatus();

        if (newStatus == SessionStatus.FINISHED) {
            redis.opsForSet().remove(INDEX_ACTIVE, idStr);
            redis.opsForZSet().remove(INDEX_WAITING, idStr);
        } else {
            redis.opsForSet().add(INDEX_ACTIVE, idStr);
            if (newStatus == SessionStatus.WAITING) {
                double score = session.getCreatedAt().toEpochMilli();
                redis.opsForZSet().add(INDEX_WAITING, idStr, score);
            } else {
                redis.opsForZSet().remove(INDEX_WAITING, idStr);
            }
        }

        if (oldStatus != null && oldStatus != newStatus) {
            redis.opsForSet().remove(STATUS_INDEX_PREFIX + oldStatus.name(), idStr);
        }
        redis.opsForSet().add(STATUS_INDEX_PREFIX + newStatus.name(), idStr);
    }

    private String key(UUID sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private String toJson(ActiveSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ActiveSession", e);
        }
    }

    private ActiveSession fromJson(String json) {
        try {
            return objectMapper.readValue(json, ActiveSession.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize ActiveSession", e);
        }
    }

    // --- Custom exceptions ---

    public static class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }

    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String message) {
            super(message);
        }
    }
}
