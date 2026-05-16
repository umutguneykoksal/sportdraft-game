package com.example.auction.simulation.entity;

import java.util.List;

/**
 * Complete result of a match simulation including final score and event timeline.
 */
public record SimulationResult(
    int homeGoals,
    int awayGoals,
    List<MatchEvent> timeline
) {
    /**
     * Returns just the score portion for backward compatibility.
     */
    public Score toScore() {
        return new Score(homeGoals, awayGoals);
    }

    /**
     * Legacy score record for existing code compatibility.
     */
    public record Score(int homeGoals, int awayGoals) {}
}
