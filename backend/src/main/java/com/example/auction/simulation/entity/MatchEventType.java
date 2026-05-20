package com.example.auction.simulation.entity;

/**
 * Types of match events for the 2D replay visualization.
 * MVP: Only important moments (goals, shots, fouls).
 */
public enum MatchEventType {
    KICK_OFF("Kick-off"),
    SHOT_SAVED("Shot saved"),
    SHOT_MISSED("Shot missed"),
    GOAL("Goal!"),
    FOUL("Foul"),
    HALF_TIME("Half-time"),
    FULL_TIME("Full-time"),
    PENALTIES_START("Penalty shoot-out"),
    PENALTY_SCORED("Penalty scored"),
    PENALTY_MISSED("Penalty missed"),
    PENALTIES_END("Penalties decided");

    private final String displayName;

    MatchEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
