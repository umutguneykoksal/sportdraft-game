package com.example.auction.session.redis;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serializes all mutations on a single game session (HTTP commands + timer scheduler).
 * Prevents optimistic-lock races between concurrent writers.
 */
@Component
public class SessionMutationGuard {

    private static final String LOCK_PREFIX = "session-lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>(
            """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                  return redis.call('del', KEYS[1])
                else
                  return 0
                end
                """,
            Long.class
        );
    }

    private final StringRedisTemplate redis;

    public SessionMutationGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public <T> T mutate(UUID sessionId, Supplier<T> action) {
        String key = lockKey(sessionId);
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session busy, retry");
        }
        try {
            return action.get();
        } finally {
            redis.execute(UNLOCK_SCRIPT, java.util.List.of(key), token);
        }
    }

    public void mutateVoid(UUID sessionId, Runnable action) {
        mutate(sessionId, () -> {
            action.run();
            return null;
        });
    }

    private String lockKey(UUID sessionId) {
        return LOCK_PREFIX + sessionId;
    }
}
