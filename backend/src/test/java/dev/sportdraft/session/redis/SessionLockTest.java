package dev.sportdraft.session.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SessionLockTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private SessionLock guard;

    @BeforeEach
    void setUp() {
        guard = new SessionLock(redis);
    }

    @Test
    void mutateRunsActionWhenLockAcquired() {
        UUID sessionId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(5)))).thenReturn(true);
        when(redis.execute(any(), any(), any())).thenReturn(1L);

        String result = guard.run(sessionId, () -> "done");

        assertThat(result).isEqualTo("done");
        verify(valueOps).setIfAbsent(eq("session-lock:" + sessionId), anyString(), eq(Duration.ofSeconds(5)));
    }

    @Test
    void mutateThrowsConflictWhenLockNotAcquired() {
        UUID sessionId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(5)))).thenReturn(false);

        assertThatThrownBy(() -> guard.run(sessionId, () -> "done"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
    }

    @Test
    void mutateRetriesWhenLockBusyThenSucceeds() {
        UUID sessionId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(5))))
            .thenReturn(false, true);
        when(redis.execute(any(), any(), any())).thenReturn(1L);

        String result = guard.run(sessionId, () -> "done");

        assertThat(result).isEqualTo("done");
    }
}
