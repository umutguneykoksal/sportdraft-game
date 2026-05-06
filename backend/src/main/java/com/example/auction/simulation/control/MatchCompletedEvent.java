package com.example.auction.simulation.control;

import java.time.Instant;
import java.util.UUID;

public record MatchCompletedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    int homeScore,
    int awayScore,
    long seed
) {
}
