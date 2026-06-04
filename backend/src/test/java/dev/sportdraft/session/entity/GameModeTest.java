package dev.sportdraft.session.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GameModeTest {

    @Test
    void fromWireOrThrow_rejectsUnknownMode() {
        assertThatThrownBy(() -> GameMode.fromWireOrThrow("INVALID"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void fromWireOrThrow_acceptsTournament() {
        assertThat(GameMode.fromWireOrThrow("tournament")).isEqualTo(GameMode.TOURNAMENT);
    }
}
