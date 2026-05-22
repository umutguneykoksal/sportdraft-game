package com.example.auction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ActiveSessionStoreIT extends AbstractIntegrationTest {

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void save_updatesStatusIndex() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.WAITING, Instant.now());
        sessionStore.save(session);

        assertThat(sessionStore.findByStatus(SessionStatus.WAITING))
            .extracting(ActiveSession::getId)
            .contains(sessionId);
        assertThat(sessionStore.findWaitingSessions())
            .extracting(ActiveSession::getId)
            .contains(sessionId);
        assertThat(sessionStore.findAllActiveIds()).contains(sessionId);

        session = sessionStore.getById(sessionId);
        long version = session.getVersion();
        session.moveToAuctioning();
        sessionStore.saveIfVersionMatch(session, version);

        assertThat(sessionStore.findByStatus(SessionStatus.AUCTIONING))
            .extracting(ActiveSession::getId)
            .contains(sessionId);
        assertThat(sessionStore.findByStatus(SessionStatus.WAITING))
            .extracting(ActiveSession::getId)
            .doesNotContain(sessionId);
        assertThat(sessionStore.findWaitingSessions())
            .extracting(ActiveSession::getId)
            .doesNotContain(sessionId);
    }

    @Test
    void saveIfVersionMatch_incrementsVersion() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.WAITING, Instant.now());
        sessionStore.save(session);

        ActiveSession loaded = sessionStore.getById(sessionId);
        assertThat(loaded.getVersion()).isEqualTo(1);

        long version = loaded.getVersion();
        loaded.moveToCountdown(Instant.now().plusSeconds(5));
        sessionStore.saveIfVersionMatch(loaded, version);

        assertThat(sessionStore.getById(sessionId).getVersion()).isEqualTo(2);
    }

    @Test
    void saveIfVersionMatch_throwsOnStaleVersion() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.WAITING, Instant.now());
        sessionStore.save(session);

        ActiveSession first = sessionStore.getById(sessionId);
        long version = first.getVersion();
        first.moveToCountdown(Instant.now().plusSeconds(5));
        sessionStore.saveIfVersionMatch(first, version);

        ActiveSession stale = sessionStore.getById(sessionId);
        stale.moveToFormationSelect(Instant.now().plusSeconds(10));

        assertThatThrownBy(() -> sessionStore.saveIfVersionMatch(stale, version))
            .isInstanceOf(ActiveSessionStore.OptimisticLockException.class);
    }

    @Test
    void delete_removesFromAllIndexes() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.WAITING, Instant.now());
        sessionStore.save(session);

        assertThat(sessionStore.exists(sessionId)).isTrue();
        assertThat(sessionStore.findAllActiveIds()).contains(sessionId);

        sessionStore.delete(sessionId);

        assertThat(sessionStore.exists(sessionId)).isFalse();
        assertThat(sessionStore.findById(sessionId)).isEmpty();
        assertThat(sessionStore.findAllActiveIds()).doesNotContain(sessionId);
        assertThat(sessionStore.findByStatus(SessionStatus.WAITING))
            .extracting(ActiveSession::getId)
            .doesNotContain(sessionId);
    }
}
