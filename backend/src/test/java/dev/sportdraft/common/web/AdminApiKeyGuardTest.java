package dev.sportdraft.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminApiKeyGuardTest {

    @Test
    void disabledWhenKeyBlank() {
        AdminApiKeyGuard guard = new AdminApiKeyGuard("");
        assertThat(guard.isEnabled()).isFalse();
        assertThatThrownBy(() -> guard.requireAdmin("any"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsMissingOrWrongKey() {
        AdminApiKeyGuard guard = new AdminApiKeyGuard("secret");
        assertThat(guard.isEnabled()).isTrue();
        assertThatThrownBy(() -> guard.requireAdmin(null))
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> guard.requireAdmin("wrong"))
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void acceptsMatchingKey() {
        AdminApiKeyGuard guard = new AdminApiKeyGuard("secret");
        guard.requireAdmin("secret");
    }
}
