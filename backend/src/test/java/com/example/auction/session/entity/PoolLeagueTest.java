package com.example.auction.session.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PoolLeagueTest {

    @Test
    void resolveDefaultsToPremierLeague() {
        assertThat(PoolLeague.resolve(null)).isEqualTo(PoolLeague.PREMIER_LEAGUE);
    }

    @Test
    void resolveAcceptsSelectableLeague() {
        assertThat(PoolLeague.resolve("PREMIER_LEAGUE")).isEqualTo(PoolLeague.PREMIER_LEAGUE);
        assertThat(PoolLeague.resolve("SUPER_LIG")).isEqualTo(PoolLeague.SUPER_LIG);
    }

    @Test
    void resolveRejectsUnavailableLeague() {
        assertThatThrownBy(() -> PoolLeague.resolve("LA_LIGA"))
            .isInstanceOf(ResponseStatusException.class);
    }
}
