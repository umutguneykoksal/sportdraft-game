package dev.sportdraft.competition.control;

import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.TournamentPhase;
import java.util.UUID;

/** Tournament round keys and phase watch/score visibility rules. */
final class TournamentRules {

    static final String FINAL_ROUND_KEY = "FINAL";
    static final String SEMI_ROUND_PREFIX = "SEMI";

    private TournamentRules() {}

    static boolean isSemi(String roundKey) {
        return roundKey != null && roundKey.startsWith(SEMI_ROUND_PREFIX);
    }

    static boolean isFinal(String roundKey) {
        return FINAL_ROUND_KEY.equals(roundKey);
    }

    static boolean isMatchParticipant(CompetitionMatchEntity match, UUID userId) {
        return userId != null && (userId.equals(match.getHomeUserId()) || userId.equals(match.getAwayUserId()));
    }

    static boolean canReplay(TournamentPhase phase, CompetitionMatchStatus status) {
        return phase == TournamentPhase.REPLAYS && status == CompetitionMatchStatus.COMPLETED;
    }

    static boolean isMandatoryWatchPending(
        TournamentPhase phase,
        CompetitionMatchEntity match,
        UUID viewerUserId,
        boolean watchedByMe,
        boolean tournamentParticipant
    ) {
        if (viewerUserId == null || watchedByMe) {
            return false;
        }
        return switch (phase) {
            case SEMIS -> isSemi(match.getRoundKey()) && isMatchParticipant(match, viewerUserId);
            case INTERMISSION -> false;
            case FINAL -> isFinal(match.getRoundKey()) && tournamentParticipant;
            case REPLAYS -> false;
        };
    }

    static boolean scoresVisibleForViewer(
        TournamentPhase phase,
        String roundKey,
        boolean watchedByMe
    ) {
        if (phase == TournamentPhase.REPLAYS) {
            return watchedByMe;
        }
        if (isSemi(roundKey) && (phase == TournamentPhase.INTERMISSION || phase == TournamentPhase.FINAL)) {
            return true;
        }
        if (isFinal(roundKey) && phase == TournamentPhase.FINAL) {
            return watchedByMe;
        }
        return watchedByMe;
    }
}
