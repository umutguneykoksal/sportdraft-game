package com.example.auction.competition.control;

import com.example.auction.session.entity.Formation;
import com.example.auction.simulation.control.DraftCompletedEvent;
import com.example.auction.simulation.control.SessionFinalizedEvent;
import java.util.List;
import java.util.UUID;

/** JSON snapshot of draft rosters for staged competition simulations. */
public record CompetitionDraftSnapshot(
    UUID sessionId,
    long seed,
    List<PlayerSnapshot> players
) {
    public record PlayerSnapshot(
        UUID userId,
        String nickname,
        Formation formation,
        List<SessionFinalizedEvent.RatedCard> roster
    ) {}

    public static CompetitionDraftSnapshot from(DraftCompletedEvent event) {
        List<PlayerSnapshot> players = event.players().stream()
            .map(p -> new PlayerSnapshot(p.userId(), p.nickname(), p.formation(), p.roster()))
            .toList();
        return new CompetitionDraftSnapshot(event.sessionId(), event.seed(), players);
    }

    public DraftCompletedEvent.DraftPlayerRoster rosterFor(UUID userId) {
        return players().stream()
            .filter(p -> p.userId().equals(userId))
            .findFirst()
            .map(p -> new DraftCompletedEvent.DraftPlayerRoster(
                p.userId(), p.nickname(), p.formation(), p.roster()
            ))
            .orElseThrow(() -> new IllegalStateException("Player not in competition snapshot: " + userId));
    }

    public int playerCount() {
        return players().size();
    }
}
