package com.example.auction.competition.control;

import com.example.auction.session.redis.ActiveSessionStore;
import java.util.UUID;

final class CompetitionMatchSupport {

    private CompetitionMatchSupport() {}

    static String nickname(CompetitionDraftSnapshot snapshot, UUID userId) {
        return snapshot.players().stream()
            .filter(p -> p.userId().equals(userId))
            .map(CompetitionDraftSnapshot.PlayerSnapshot::nickname)
            .findFirst()
            .orElse("Player");
    }

    static String formation(CompetitionDraftSnapshot snapshot, UUID userId) {
        return snapshot.players().stream()
            .filter(p -> p.userId().equals(userId))
            .map(p -> p.formation().name())
            .findFirst()
            .orElse("FOUR_FOUR_TWO");
    }

    static void finishSessionIfPresent(ActiveSessionStore sessionStore, UUID sessionId) {
        sessionStore.findById(sessionId).ifPresent(session -> {
            long vBefore = session.getVersion();
            session.moveToFinished();
            sessionStore.saveIfVersionMatch(session, vBefore);
        });
    }
}
