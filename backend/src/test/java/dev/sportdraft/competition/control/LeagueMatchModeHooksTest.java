package dev.sportdraft.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.competition.entity.LeaguePhase;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.simulation.entity.SimulationResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LeagueMatchModeHooksTest {

    @Mock
    private LeagueFixtureService leagueService;

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private LeagueProgressNotifier progressNotifier;

    @Mock
    private LeagueVisibilityPolicy visibilityPolicy;

    @Mock
    private ActiveSessionStore sessionStore;

    private LeagueMatchModeHooks hooks;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        hooks = new LeagueMatchModeHooks(
            leagueService,
            competitionRepository,
            progressNotifier,
            visibilityPolicy,
            sessionStore
        );
    }

    @Test
    void validateOpen_rejectsReplayBeforeReplaysPhase() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.MATCHDAY_1);
        CompetitionMatchEntity match = completedMatch("MD1_A");

        when(visibilityPolicy.replayAllowed(competition, match)).thenReturn(false);

        assertThatThrownBy(() -> hooks.validateOpen(competition, match, userId, true, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void validateOpen_rejectsDuringMatchdayBreak() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.BREAK_AFTER_MD1);
        CompetitionMatchEntity match = completedMatch("MD1_A");

        assertThatThrownBy(() -> hooks.validateOpen(competition, match, userId, false, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void simulateIfNeeded_delegatesToFixtureService() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.MATCHDAY_1);
        CompetitionMatchEntity match = pendingMatch("MD1_A");
        SimulationResult expected = new SimulationResult(2, 1, List.of(), 0, 0);

        when(leagueService.simulateMatchIfNeeded(competition, match)).thenReturn(expected);

        assertThat(hooks.simulateIfNeeded(competition, match)).isSameAs(expected);
    }

    @Test
    void afterWatchRecorded_triggersBreakAndNotifier() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.MATCHDAY_1);
        CompetitionEntity reloaded = leagueCompetition(LeaguePhase.BREAK_AFTER_MD1);

        when(leagueService.tryStartMatchdayBreak(competition)).thenReturn(true);
        when(competitionRepository.findById(competition.getId())).thenReturn(Optional.of(reloaded));

        CompetitionEntity result = hooks.afterWatchRecorded(sessionId, competition);

        assertThat(result.getLeaguePhase()).isEqualTo(LeaguePhase.BREAK_AFTER_MD1);
        verify(progressNotifier).notifyBreakOrPlayoffIfStarted(eq(sessionId), eq(reloaded), eq(true));
        verify(progressNotifier).notifyFinalState(eq(sessionId), eq(reloaded), any());
    }

    @Test
    void type_isLeague() {
        assertThat(hooks.type()).isEqualTo(CompetitionType.LEAGUE);
    }

    private CompetitionEntity leagueCompetition(LeaguePhase phase) {
        CompetitionEntity competition = new CompetitionEntity();
        competition.setId(UUID.randomUUID());
        competition.setSessionId(sessionId);
        competition.setType(CompetitionType.LEAGUE);
        competition.setLeaguePhase(phase);
        return competition;
    }

    private CompetitionMatchEntity pendingMatch(String roundKey) {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setRoundKey(roundKey);
        match.setStatus(CompetitionMatchStatus.PENDING);
        return match;
    }

    private CompetitionMatchEntity completedMatch(String roundKey) {
        CompetitionMatchEntity match = pendingMatch(roundKey);
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        return match;
    }
}
