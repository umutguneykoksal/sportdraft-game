package dev.sportdraft.integration;

import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Helpers for integration tests that mutate auction rounds in Redis. */
final class IntegrationAuctionTestSupport {

    private static final long ROUND_REVEAL_SECONDS = 2L;

    private IntegrationAuctionTestSupport() {}

    static void openBiddingOnCurrentRound(ActiveSessionStore sessionStore, UUID sessionId) {
        ActiveSession session = sessionStore.getById(sessionId);
        ActiveRound openRound = session.findOpenRound()
            .orElseThrow(() -> new IllegalStateException("No open round in session " + sessionId));
        openRound.setStartsAt(Instant.now().minus(ROUND_REVEAL_SECONDS + 1, ChronoUnit.SECONDS));
        sessionStore.saveIfVersionMatch(session, session.getVersion());
    }
}
