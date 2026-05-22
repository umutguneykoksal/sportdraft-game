package dev.sportdraft.session.control;

import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSession;
import java.time.Duration;
import java.util.Set;

public final class SessionDisconnectPolicy {

    static final Set<SessionStatus> FORFEIT_ELIGIBLE_STATUSES = Set.of(
        SessionStatus.AUCTIONING,
        SessionStatus.MATCH_START_PENDING,
        SessionStatus.FORMATION_SELECT,
        SessionStatus.COUNTDOWN
    );

    static final Set<SessionStatus> LOBBY_PHASE_STATUSES = Set.of(
        SessionStatus.WAITING,
        SessionStatus.COUNTDOWN,
        SessionStatus.FORMATION_SELECT
    );

    private static final Duration FINISHED_ARCHIVE_GRACE_PERIOD = Duration.ofMinutes(2);

    private SessionDisconnectPolicy() {}

    static boolean isLobbyPhase(SessionStatus status) {
        return LOBBY_PHASE_STATUSES.contains(status);
    }

    static boolean isForfeitEligible(SessionStatus status) {
        return FORFEIT_ELIGIBLE_STATUSES.contains(status);
    }

    static boolean shouldResetLobbyAfterLeave(ActiveSession session, SessionStatus statusBeforeLeave) {
        if (session.playerCount() >= session.getMaxPlayers()) {
            return false;
        }
        return statusBeforeLeave == SessionStatus.COUNTDOWN
            || statusBeforeLeave == SessionStatus.FORMATION_SELECT;
    }

    static boolean shouldSkipDisconnectDraw(ActiveSession session) {
        if (session.getMaxPlayers() > 2) {
            return true;
        }
        GameMode mode = session.getGameMode();
        if (mode == GameMode.TOURNAMENT || mode == GameMode.LEAGUE) {
            return true;
        }
        SessionStatus status = session.getStatus();
        return status == SessionStatus.AWAITING_RESULT
            || status == SessionStatus.FINALIZING
            || status == SessionStatus.MATCH_START_PENDING;
    }

    static Duration lobbyDisconnectTimeout(ActiveSession session, Duration defaultTimeout, Duration tournamentTimeout) {
        if (session.getGameMode() == GameMode.TOURNAMENT) {
            return tournamentTimeout;
        }
        return defaultTimeout;
    }

    static Duration finishedGracePeriod(ActiveSession session, Duration tournamentRetention) {
        if (session.getGameMode() == GameMode.TOURNAMENT) {
            return tournamentRetention;
        }
        return FINISHED_ARCHIVE_GRACE_PERIOD;
    }

    static boolean shouldArchiveTournamentWithoutPostgres(ActiveSession session) {
        return session.getGameMode() == GameMode.TOURNAMENT;
    }

    public static boolean shouldResetLobby(ActiveSession session) {
        if (session == null) {
            return false;
        }
        return session.playerCount() < session.getMaxPlayers()
            && (session.getStatus() == SessionStatus.COUNTDOWN
                || session.getStatus() == SessionStatus.FORMATION_SELECT);
    }
}
