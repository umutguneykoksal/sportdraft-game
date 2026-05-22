package dev.sportdraft.simulation.control;

import dev.sportdraft.bidding.entity.PlayerAttributes;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.session.entity.Formation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionFinalizedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    long seed,
    Formation homeFormation,
    Formation awayFormation,
    List<RatedCard> homeRoster,
    List<RatedCard> awayRoster,
    UUID homePlayerId,
    UUID awayPlayerId
) {
    public record RatedCard(
        UUID id,
        Position position,
        int rating,
        String name,
        PlayerAttributes attributes
    ) {}
}
