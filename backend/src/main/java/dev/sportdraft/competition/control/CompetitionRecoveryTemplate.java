package dev.sportdraft.competition.control;

import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveSession;

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
