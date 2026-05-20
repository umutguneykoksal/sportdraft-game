package com.example.auction.simulation.control;

import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DraftCompletedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    GameMode gameMode,
    long seed,
    List<DraftPlayerRoster> players
) {
    public record DraftPlayerRoster(
        UUID userId,
        String nickname,
        Formation formation,
        List<SessionFinalizedEvent.RatedCard> roster
    ) {}
}
