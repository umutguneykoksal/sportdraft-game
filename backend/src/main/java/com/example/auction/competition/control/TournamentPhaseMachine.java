package com.example.auction.competition.control;

import com.example.auction.competition.entity.TournamentPhase;
import java.time.Instant;

final class TournamentPhaseMachine {

    private TournamentPhaseMachine() {}

    static boolean canStartIntermission(TournamentPhase phase) {
        return phase == TournamentPhase.SEMIS;
    }

    static boolean canAdvanceToFinal(TournamentPhase phase, Instant intermissionEndsAt, Instant now) {
        if (phase != TournamentPhase.INTERMISSION) {
            return false;
        }
        return intermissionEndsAt != null && !now.isBefore(intermissionEndsAt);
    }

    static boolean canCompleteTournament(TournamentPhase phase) {
        return phase == TournamentPhase.FINAL;
    }
}
