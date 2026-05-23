package dev.sportdraft.feedback.control;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FeedbackRateLimiter {

    private static final String KEY_PREFIX = "feedback-rate:";
    private static final int MAX_PER_HOUR = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    public FeedbackRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void checkAllowed(String clientIp) {
        String key = KEY_PREFIX + clientIp;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
        if (count != null && count > MAX_PER_HOUR) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many feedback submissions — try again later"
            );
        }
    }
}
