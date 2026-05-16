package com.example.auction.simulation.control;

import com.example.auction.simulation.entity.MatchEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchCompletedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    int homeScore,
    int awayScore,
    long seed,
    List<MatchEvent> timeline
) {
}
