package com.example.auction.competition.control;

import com.example.auction.competition.entity.LeaguePhase;
import java.time.Instant;

final class LeaguePhaseMachine {

    private LeaguePhaseMachine() {}

    static boolean canEvaluateMatchdayBreak(LeaguePhase phase) {
        int md = phase.matchdayNumber();
        return md > 0 && !phase.isMatchdayBreak();
    }

    static boolean canAdvanceFromBreak(LeaguePhase phase, Instant intermissionEndsAt, Instant now) {
        if (!phase.isMatchdayBreak()) {
            return false;
        }
        return intermissionEndsAt != null && !now.isBefore(intermissionEndsAt);
    }

    static boolean shouldResolveRoundRobin(LeaguePhase phase, int matchday) {
        return phase == LeaguePhase.MATCHDAY_3 && matchday >= 3;
    }
}
