package com.example.auction.simulation.control;

import com.example.auction.bidding.entity.Position;
import com.example.auction.session.entity.Formation;
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
    List<RatedCard> awayRoster
) {
    public record RatedCard(UUID id, Position position, int rating) {}
}
