package dev.sportdraft.competition.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.TournamentPhase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TournamentRulesTest {

    private final UUID home = UUID.randomUUID();
    private final UUID away = UUID.randomUUID();
    private final UUID spectator = UUID.randomUUID();

    @Test
    void mandatoryWatch_semiParticipantOnly() {
        CompetitionMatchEntity match = match("SEMI_1", home, away);

        assertThat(TournamentRules.isMandatoryWatchPending(TournamentPhase.SEMIS, match, home, false, true)).isTrue();
        assertThat(TournamentRules.isMandatoryWatchPending(TournamentPhase.SEMIS, match, spectator, false, true)).isFalse();
    }

    @Test
    void mandatoryWatch_finalRequiresTournamentParticipant() {
        CompetitionMatchEntity match = match(TournamentRules.FINAL_ROUND_KEY, home, away);

        assertThat(TournamentRules.isMandatoryWatchPending(TournamentPhase.FINAL, match, spectator, false, true)).isTrue();
        assertThat(TournamentRules.isMandatoryWatchPending(TournamentPhase.FINAL, match, spectator, false, false)).isFalse();
    }

    @Test
    void scoresVisible_semisPublicAfterIntermission() {
        assertThat(TournamentRules.scoresVisibleForViewer(TournamentPhase.INTERMISSION, "SEMI_1", false)).isTrue();
        assertThat(TournamentRules.scoresVisibleForViewer(TournamentPhase.SEMIS, "SEMI_1", false)).isFalse();
    }

    @Test
    void scoresVisible_finalRequiresWatchDuringFinalPhase() {
        assertThat(TournamentRules.scoresVisibleForViewer(TournamentPhase.FINAL, TournamentRules.FINAL_ROUND_KEY, false))
            .isFalse();
        assertThat(TournamentRules.scoresVisibleForViewer(TournamentPhase.FINAL, TournamentRules.FINAL_ROUND_KEY, true))
            .isTrue();
    }

    @Test
    void canReplay_onlyInReplaysPhase() {
        assertThat(TournamentRules.canReplay(TournamentPhase.REPLAYS, CompetitionMatchStatus.COMPLETED)).isTrue();
        assertThat(TournamentRules.canReplay(TournamentPhase.FINAL, CompetitionMatchStatus.COMPLETED)).isFalse();
    }

    private static CompetitionMatchEntity match(String roundKey, UUID homeUserId, UUID awayUserId) {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setRoundKey(roundKey);
        match.setHomeUserId(homeUserId);
        match.setAwayUserId(awayUserId);
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        return match;
    }
}
