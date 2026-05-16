package com.example.auction.simulation.entity;

import java.util.UUID;

/**
 * A single event in the match timeline for 2D replay visualization.
 * Contains minute, type, team, player info, and descriptive detail.
 */
public record MatchEvent(
    int minute,
    MatchEventType type,
    UUID teamId,
    UUID playerId,
    String playerName,
    String detail
) {
    /**
     * Creates a generic event (kick-off, half-time, full-time) with no specific player.
     */
    public static MatchEvent generic(int minute, MatchEventType type, String detail) {
        return new MatchEvent(minute, type, null, null, null, detail);
    }

    /**
     * Creates a team event (e.g., possession change) with no specific player.
     */
    public static MatchEvent forTeam(int minute, MatchEventType type, UUID teamId, String detail) {
        return new MatchEvent(minute, type, teamId, null, null, detail);
    }

    /**
     * Creates a player-specific event (goal, shot, foul).
     */
    public static MatchEvent forPlayer(int minute, MatchEventType type, UUID teamId, 
                                        UUID playerId, String playerName, String detail) {
        return new MatchEvent(minute, type, teamId, playerId, playerName, detail);
    }
}
