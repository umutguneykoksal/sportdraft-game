package com.example.auction.session.redis;

import com.example.auction.session.entity.SessionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Redis-backed store for active game sessions.
 * Each session is stored as a single JSON document at key "session:{id}".
 * Secondary index keys maintain sets for lookup by status.
 */
@Component
public class ActiveSessionStore {

    private static final String KEY_PREFIX = "session:";
    private static final String INDEX_ACTIVE = "sessions:active";
    private static final String INDEX_WAITING = "sessions:waiting";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ActiveSessionStore(StringRedisTemplate redis,
                              @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // --- CRUD ---

    public void save(ActiveSession session) {
        session.setVersion(session.getVersion() + 1);
        String key = key(session.getId());
        String json = toJson(session);
        redis.opsForValue().set(key, json, SESSION_TTL);
        updateIndexes(session);
    }

    /**
     * Optimistic save: uses WATCH/MULTI to ensure no concurrent modification.
     * Throws OptimisticLockException if the version in Redis has changed
     * since the session was read.
     */
    public void saveIfVersionMatch(ActiveSession session, long expectedVersion) {
        String key = key(session.getId());

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
        updateIndexes(session);
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
        redis.delete(key(sessionId));
        String idStr = sessionId.toString();
        redis.opsForSet().remove(INDEX_ACTIVE, idStr);
        redis.opsForZSet().remove(INDEX_WAITING, idStr);
    }

    public boolean exists(UUID sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(key(sessionId)));
    }

    // --- Index-based queries ---

    /**
     * Returns all WAITING sessions sorted by createdAt (earliest first).
     * Used by lobby matchmaking.
     */
    public List<ActiveSession> findWaitingSessions() {
        Set<String> ids = redis.opsForZSet().range(INDEX_WAITING, 0, -1);
        if (ids == null || ids.isEmpty()) return List.of();
        List<ActiveSession> sessions = new ArrayList<>();
        for (String idStr : ids) {
            findById(UUID.fromString(idStr))
                .filter(s -> s.getStatus() == SessionStatus.WAITING)
                .ifPresent(sessions::add);
        }
        return sessions;
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
     */
    public List<ActiveSession> findByStatus(SessionStatus status) {
        List<ActiveSession> result = new ArrayList<>();
        Set<String> ids = redis.opsForSet().members(INDEX_ACTIVE);
        if (ids == null) return result;
        for (String idStr : ids) {
            findById(UUID.fromString(idStr))
                .filter(s -> s.getStatus() == status)
                .ifPresent(result::add);
        }
        return result;
    }

    /**
     * Returns all active sessions NOT matching the given status.
     */
    public List<ActiveSession> findByStatusNot(SessionStatus status) {
        List<ActiveSession> result = new ArrayList<>();
        Set<String> ids = redis.opsForSet().members(INDEX_ACTIVE);
        if (ids == null) return result;
        for (String idStr : ids) {
            findById(UUID.fromString(idStr))
                .filter(s -> s.getStatus() != status)
                .ifPresent(result::add);
        }
        return result;
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
        return count;
    }

    // --- Internal ---

    private void updateIndexes(ActiveSession session) {
        String idStr = session.getId().toString();
        if (session.getStatus() == SessionStatus.FINISHED) {
            redis.opsForSet().remove(INDEX_ACTIVE, idStr);
            redis.opsForZSet().remove(INDEX_WAITING, idStr);
        } else {
            redis.opsForSet().add(INDEX_ACTIVE, idStr);
            if (session.getStatus() == SessionStatus.WAITING) {
                double score = session.getCreatedAt().toEpochMilli();
                redis.opsForZSet().add(INDEX_WAITING, idStr, score);
            } else {
                redis.opsForZSet().remove(INDEX_WAITING, idStr);
            }
        }
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
