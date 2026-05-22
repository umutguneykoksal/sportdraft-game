package dev.sportdraft.competition.control;

import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.LeaguePhase;
import java.util.UUID;

/** Round-robin round keys and league phase watch/score visibility rules. */
final class LeagueRules {

    static final String PLAYOFF_ROUND_KEY = "PLAYOFF_FINAL";
    static final int PARTICIPANTS_PER_MATCH = 2;

    private LeagueRules() {}

    static boolean isPlayoff(String roundKey) {
        return PLAYOFF_ROUND_KEY.equals(roundKey);
    }

    static boolean isRoundRobin(String roundKey) {
        return roundKey != null && roundKey.startsWith("MD");
    }

    static int matchdayFromRoundKey(String roundKey) {
        if (!isRoundRobin(roundKey)) {
            return 0;
        }
        try {
            int underscore = roundKey.indexOf('_');
            if (underscore < 0) {
                return 0;
            }
            return Integer.parseInt(roundKey.substring(2, underscore));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    static boolean belongsToMatchday(String roundKey, int matchday) {
        return matchday > 0 && roundKey.startsWith("MD" + matchday + "_");
    }

    static boolean isMatchParticipant(CompetitionMatchEntity match, UUID userId) {
        return userId != null && (userId.equals(match.getHomeUserId()) || userId.equals(match.getAwayUserId()));
    }

    static boolean canReplay(LeaguePhase phase, CompetitionMatchStatus status) {
        return phase == LeaguePhase.REPLAYS && status == CompetitionMatchStatus.COMPLETED;
    }

    static boolean isMandatoryWatchPending(
        LeaguePhase phase,
        CompetitionMatchEntity match,
        UUID viewerUserId,
        boolean watchedByMe
    ) {
        if (viewerUserId == null || watchedByMe) {
            return false;
        }
        return switch (phase) {
            case MATCHDAY_1, MATCHDAY_2, MATCHDAY_3 ->
                belongsToMatchday(match.getRoundKey(), phase.matchdayNumber())
                    && isMatchParticipant(match, viewerUserId);
            case CHAMPIONSHIP_PLAYOFF ->
                isPlayoff(match.getRoundKey()) && isMatchParticipant(match, viewerUserId);
            case BREAK_AFTER_MD1, BREAK_AFTER_MD2, COMPLETED, REPLAYS -> false;
        };
    }

    static boolean scoresVisibleForViewer(
        LeaguePhase phase,
        String roundKey,
        boolean watchedByMe
    ) {
        if (phase == LeaguePhase.REPLAYS) {
            return watchedByMe;
        }
        if (isPlayoff(roundKey)) {
            return phase == LeaguePhase.COMPLETED || watchedByMe;
        }
        int matchday = matchdayFromRoundKey(roundKey);
        if (publicScoresVisible(phase, matchday)) {
            return true;
        }
        return watchedByMe;
    }

    private static boolean publicScoresVisible(LeaguePhase phase, int matchday) {
        return switch (phase) {
            case BREAK_AFTER_MD1 -> matchday == 1;
            case BREAK_AFTER_MD2 -> matchday <= 2;
            case CHAMPIONSHIP_PLAYOFF, COMPLETED -> true;
            case MATCHDAY_1 -> matchday < 1;
            case MATCHDAY_2 -> matchday < 2;
            case MATCHDAY_3 -> matchday < 3;
            case REPLAYS -> false;
        };
    }
}
