package dev.sportdraft.session.control;

import java.util.UUID;

/** Home/away perspective for match result and timeline views. */
record SessionPerspective(UUID viewerUserId, UUID homeUserId, UUID awayUserId) {

    static SessionPerspective of(UUID viewerUserId, UUID homeUserId, UUID awayUserId) {
        return new SessionPerspective(viewerUserId, homeUserId, awayUserId);
    }

    boolean isParticipant() {
        return viewerUserId.equals(homeUserId) || viewerUserId.equals(awayUserId);
    }

    boolean isHome() {
        return viewerUserId.equals(homeUserId);
    }

    int myScore(int homeScore, int awayScore) {
        return isHome() ? homeScore : awayScore;
    }

    int opponentScore(int homeScore, int awayScore) {
        return isHome() ? awayScore : homeScore;
    }

    int myPenaltyScore(int homePenaltyScore, int awayPenaltyScore) {
        return isHome() ? homePenaltyScore : awayPenaltyScore;
    }

    int opponentPenaltyScore(int homePenaltyScore, int awayPenaltyScore) {
        return isHome() ? awayPenaltyScore : homePenaltyScore;
    }

    String myNickname(String homeNickname, String awayNickname) {
        return isHome() ? homeNickname : awayNickname;
    }

    String opponentNickname(String homeNickname, String awayNickname) {
        return isHome() ? awayNickname : homeNickname;
    }
}
