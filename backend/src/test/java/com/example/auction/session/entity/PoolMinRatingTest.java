package com.example.auction.session.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PoolMinRatingTest {

    @Test
    void resolveDefaultsToSeventy() {
        assertThat(PoolMinRating.resolve(null)).isEqualTo(70);
    }

    @Test
    void resolveAcceptsAllowedValues() {
        assertThat(PoolMinRating.resolve(75)).isEqualTo(75);
        assertThat(PoolMinRating.resolve(80)).isEqualTo(80);
    }

    @Test
    void resolveRejectsUnknownValues() {
        assertThatThrownBy(() -> PoolMinRating.resolve(85))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void effectiveTreatsUnsetLegacyAsDefault() {
        assertThat(PoolMinRating.effective(0)).isEqualTo(70);
        assertThat(PoolMinRating.effective(80)).isEqualTo(80);
    }
}
