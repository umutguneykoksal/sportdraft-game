package dev.sportdraft.competition.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.LeaguePhase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeagueRulesTest {

    private final UUID home = UUID.randomUUID();
    private final UUID away = UUID.randomUUID();
    private final UUID spectator = UUID.randomUUID();

    @Test
    void mandatoryWatch_activeMatchdayParticipant() {
        CompetitionMatchEntity match = match("MD1_A", home, away);

        assertThat(LeagueRules.isMandatoryWatchPending(LeaguePhase.MATCHDAY_1, match, home, false)).isTrue();
        assertThat(LeagueRules.isMandatoryWatchPending(LeaguePhase.MATCHDAY_1, match, spectator, false)).isFalse();
        assertThat(LeagueRules.isMandatoryWatchPending(LeaguePhase.MATCHDAY_1, match, home, true)).isFalse();
    }

    @Test
    void mandatoryWatch_playoffParticipantOnly() {
        CompetitionMatchEntity match = match(LeagueRules.PLAYOFF_ROUND_KEY, home, away);

        assertThat(LeagueRules.isMandatoryWatchPending(LeaguePhase.CHAMPIONSHIP_PLAYOFF, match, home, false)).isTrue();
        assertThat(LeagueRules.isMandatoryWatchPending(LeaguePhase.MATCHDAY_3, match, home, false)).isFalse();
    }

    @Test
    void mandatoryWatch_notDuringBreakOrReplays() {
        CompetitionMatchEntity match = match("MD2_A", home, away);

        assertThat(LeagueRules.isMandatoryWatchPending(LeaguePhase.BREAK_AFTER_MD1, match, home, false)).isFalse();
        assertThat(LeagueRules.isMandatoryWatchPending(LeaguePhase.REPLAYS, match, home, false)).isFalse();
    }

    @Test
    void scoresVisible_publicAfterMatchdayBreak() {
        assertThat(LeagueRules.scoresVisibleForViewer(LeaguePhase.BREAK_AFTER_MD1, "MD1_A", false)).isTrue();
        assertThat(LeagueRules.scoresVisibleForViewer(LeaguePhase.BREAK_AFTER_MD1, "MD2_A", false)).isFalse();
    }

    @Test
    void scoresVisible_playoffOnlyWhenCompletedOrWatched() {
        assertThat(LeagueRules.scoresVisibleForViewer(LeaguePhase.CHAMPIONSHIP_PLAYOFF, LeagueRules.PLAYOFF_ROUND_KEY, false))
            .isFalse();
        assertThat(LeagueRules.scoresVisibleForViewer(LeaguePhase.COMPLETED, LeagueRules.PLAYOFF_ROUND_KEY, false))
            .isTrue();
        assertThat(LeagueRules.scoresVisibleForViewer(LeaguePhase.CHAMPIONSHIP_PLAYOFF, LeagueRules.PLAYOFF_ROUND_KEY, true))
            .isTrue();
    }

    @Test
    void scoresVisible_replaysRequirePersonalWatch() {
        assertThat(LeagueRules.scoresVisibleForViewer(LeaguePhase.REPLAYS, "MD1_A", false)).isFalse();
        assertThat(LeagueRules.scoresVisibleForViewer(LeaguePhase.REPLAYS, "MD1_A", true)).isTrue();
    }

    @Test
    void canReplay_onlyInReplaysPhase() {
        assertThat(LeagueRules.canReplay(LeaguePhase.REPLAYS, CompetitionMatchStatus.COMPLETED)).isTrue();
        assertThat(LeagueRules.canReplay(LeaguePhase.MATCHDAY_1, CompetitionMatchStatus.COMPLETED)).isFalse();
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
