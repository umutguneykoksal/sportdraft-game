package com.example.auction.competition.control;

import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;

final class CompetitionRecoveryTemplate {

    private CompetitionRecoveryTemplate() {}

    static boolean isEligibleForRecovery(ActiveSession session, GameMode expectedMode) {
        if (session == null || session.getGameMode() != expectedMode) {
            return false;
        }
        if (session.getStatus() != SessionStatus.FINALIZING
            && session.getStatus() != SessionStatus.AWAITING_RESULT) {
            return false;
        }
        if (session.playerCount() < session.getMaxPlayers()) {
            return false;
        }
        return session.getPlayers().stream().allMatch(ActivePlayer::isRosterFull);
    }
}
