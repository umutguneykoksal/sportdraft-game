package dev.sportdraft.session.redis;

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
 * Retries lock contention and optimistic version conflicts before surfacing 409 to the client.
 */
@Component
public class SessionMutationGuard {

    private static final String LOCK_PREFIX = "session-lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_BASE_MS = 25L;
    private static final String SESSION_BUSY = "Session busy, retry";

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
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return runWithLock(sessionId, action);
            } catch (ActiveSessionStore.OptimisticLockException e) {
                if (attempt == MAX_ATTEMPTS - 1) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            } catch (ResponseStatusException e) {
                if (!isSessionBusy(e) || attempt == MAX_ATTEMPTS - 1) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    public void mutateVoid(UUID sessionId, Runnable action) {
        mutate(sessionId, () -> {
            action.run();
            return null;
        });
    }

    private <T> T runWithLock(UUID sessionId, Supplier<T> action) {
        String key = lockKey(sessionId);
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, SESSION_BUSY);
        }
        try {
            return action.get();
        } finally {
            redis.execute(UNLOCK_SCRIPT, java.util.List.of(key), token);
        }
    }

    private static boolean isSessionBusy(ResponseStatusException e) {
        return e.getStatusCode() == HttpStatus.CONFLICT && SESSION_BUSY.equals(e.getReason());
    }

    private static void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BASE_MS * (attempt + 1));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent update — please retry");
        }
    }

    private String lockKey(UUID sessionId) {
        return LOCK_PREFIX + sessionId;
    }
}
