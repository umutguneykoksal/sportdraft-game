package dev.sportdraft.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.competition.entity.TournamentPhase;
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
class TournamentMatchModeHooksTest {

    @Mock
    private TournamentBracketService bracketService;

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private TournamentProgressNotifier progressNotifier;

    @Mock
    private TournamentVisibilityPolicy visibilityPolicy;

    @Mock
    private ActiveSessionStore sessionStore;

    private TournamentMatchModeHooks hooks;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID homeUserId = UUID.randomUUID();
    private final UUID awayUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        hooks = new TournamentMatchModeHooks(
            bracketService,
            competitionRepository,
            progressNotifier,
            visibilityPolicy,
            sessionStore
        );
    }

    @Test
    void validateOpen_rejectsReplayBeforeReplaysPhase() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.SEMIS);
        CompetitionMatchEntity match = completedSemi();

        when(visibilityPolicy.replayAllowed(competition, match)).thenReturn(false);

        assertThatThrownBy(() -> hooks.validateOpen(competition, match, homeUserId, true, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void validateOpen_rejectsNonParticipantOnSemi() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.SEMIS);
        CompetitionMatchEntity match = completedSemi();
        UUID outsider = UUID.randomUUID();

        when(bracketService.isMatchParticipant(match, outsider)).thenReturn(false);

        assertThatThrownBy(() -> hooks.validateOpen(competition, match, outsider, false, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void validateOpen_rejectsDuringIntermission() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.INTERMISSION);
        CompetitionMatchEntity match = completedSemi();

        assertThatThrownBy(() -> hooks.validateOpen(competition, match, homeUserId, false, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void simulateIfNeeded_delegatesToBracketService() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.SEMIS);
        CompetitionMatchEntity match = pendingSemi();
        SimulationResult expected = new SimulationResult(1, 0, List.of(), 0, 0);

        when(bracketService.simulateMatchIfNeeded(competition, match)).thenReturn(expected);

        assertThat(hooks.simulateIfNeeded(competition, match)).isSameAs(expected);
    }

    @Test
    void afterWatchRecorded_startsIntermissionWhenReady() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.SEMIS);
        CompetitionEntity intermission = tournamentCompetition(TournamentPhase.INTERMISSION);

        when(bracketService.tryStartIntermissionIfReady(competition)).thenReturn(true);
        when(competitionRepository.findById(competition.getId())).thenReturn(Optional.of(intermission));
        when(bracketService.tryCompleteTournament(intermission)).thenReturn(intermission);

        CompetitionEntity result = hooks.afterWatchRecorded(sessionId, competition);

        assertThat(result.getTournamentPhase()).isEqualTo(TournamentPhase.INTERMISSION);
        verify(progressNotifier).notifyIntermissionStarted(sessionId, intermission);
        verify(progressNotifier).notifyFinalState(eq(sessionId), eq(intermission), any());
    }

    @Test
    void type_isTournament() {
        assertThat(hooks.type()).isEqualTo(CompetitionType.TOURNAMENT);
    }

    private CompetitionEntity tournamentCompetition(TournamentPhase phase) {
        CompetitionEntity competition = new CompetitionEntity();
        competition.setId(UUID.randomUUID());
        competition.setSessionId(sessionId);
        competition.setType(CompetitionType.TOURNAMENT);
        competition.setTournamentPhase(phase);
        return competition;
    }

    private CompetitionMatchEntity pendingSemi() {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setRoundKey("SEMI_1");
        match.setHomeUserId(homeUserId);
        match.setAwayUserId(awayUserId);
        match.setStatus(CompetitionMatchStatus.PENDING);
        return match;
    }

    private CompetitionMatchEntity completedSemi() {
        CompetitionMatchEntity match = pendingSemi();
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        return match;
    }
}
