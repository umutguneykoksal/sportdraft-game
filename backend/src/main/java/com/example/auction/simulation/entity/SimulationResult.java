package com.example.auction.simulation.entity;

import java.util.List;
import java.util.UUID;

/**
 * Complete result of a match simulation including final score and event timeline.
 */
public record SimulationResult(
    int homeGoals,
    int awayGoals,
    List<MatchEvent> timeline,
    int homePenaltyGoals,
    int awayPenaltyGoals
) {
    public SimulationResult(int homeGoals, int awayGoals, List<MatchEvent> timeline) {
        this(homeGoals, awayGoals, timeline, 0, 0);
    }

    public boolean decidedOnPenalties() {
        return homeGoals == awayGoals && (homePenaltyGoals > 0 || awayPenaltyGoals > 0);
    }

    /**
     * Returns just the regulation score portion for legacy code compatibility.
     */
    public Score toScore() {
        return new Score(homeGoals, awayGoals);
    }

    public UUID resolveWinner(UUID homeTeamId, UUID awayTeamId) {
        if (homeGoals > awayGoals) {
            return homeTeamId;
        }
        if (awayGoals > homeGoals) {
            return awayTeamId;
        }
        if (homePenaltyGoals > awayPenaltyGoals) {
            return homeTeamId;
        }
        if (awayPenaltyGoals > homePenaltyGoals) {
            return awayTeamId;
        }
        return homeTeamId;
    }

    /**
     * Legacy score record for existing code compatibility.
     */
    public record Score(int homeGoals, int awayGoals) {}
}
