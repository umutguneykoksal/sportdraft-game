package dev.sportdraft.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.competition.boundary.CompetitionResponse;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.CompetitionMatchViewRepository;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionStatus;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.simulation.entity.SimulationResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompetitionMatchCommandServiceTest {

    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private CompetitionMatchRepository matchRepository;
    @Mock
    private CompetitionMatchViewRepository viewRepository;
    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private CompetitionQueryService competitionQueryService;
    @Mock
    private CompetitionModeBehavior tournamentHooks;
    @Mock
    private CompetitionModeBehavior leagueHooks;

    private CompetitionMatchCommandService commandService;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID competitionId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();
    private final UUID homeUserId = UUID.randomUUID();
    private final UUID awayUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(tournamentHooks.type()).thenReturn(CompetitionType.TOURNAMENT);
        when(leagueHooks.type()).thenReturn(CompetitionType.LEAGUE);
        commandService = new CompetitionMatchCommandService(
            competitionRepository,
            matchRepository,
            viewRepository,
            sessionStore,
            competitionQueryService,
            List.of(tournamentHooks, leagueHooks)
        );
    }

    @Test
    void openMatch_hidesScoresUntilWatched() {
        CompetitionEntity competition = tournamentCompetition();
        CompetitionMatchEntity match = completedMatch();
        CompetitionDraftSnapshot snapshot = draftSnapshot();
        SimulationResult result = new SimulationResult(2, 1, List.of());

        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(competition));
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(activeSession(homeUserId)));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(viewRepository.existsByMatchIdAndUserId(matchId, homeUserId)).thenReturn(false);
        when(tournamentHooks.simulateIfNeeded(competition, match)).thenReturn(result);
        when(tournamentHooks.readSnapshot(competition)).thenReturn(snapshot);
        when(tournamentHooks.requiredWatchers(competition, match)).thenReturn(2);
        when(tournamentHooks.matchFullyWatched(competition, match)).thenReturn(false);
        when(viewRepository.countByMatchId(matchId)).thenReturn(0L);

        var response = commandService.openMatch(sessionId, matchId, homeUserId, false, CompetitionType.TOURNAMENT);

        assertThat(response.matchId()).isEqualTo(matchId);
        assertThat(response.homeScore()).isNull();
        assertThat(response.awayScore()).isNull();
        assertThat(response.watchedByMe()).isFalse();
        verify(tournamentHooks).validateOpen(competition, match, homeUserId, false, false);
    }

    @Test
    void openMatch_replayShowsScores() {
        CompetitionEntity competition = tournamentCompetition();
        CompetitionMatchEntity match = completedMatch();
        CompetitionDraftSnapshot snapshot = draftSnapshot();
        SimulationResult result = new SimulationResult(3, 2, List.of());

        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(competition));
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(activeSession(homeUserId)));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(viewRepository.existsByMatchIdAndUserId(matchId, homeUserId)).thenReturn(true);
        when(tournamentHooks.simulateIfNeeded(competition, match)).thenReturn(result);
        when(tournamentHooks.readSnapshot(competition)).thenReturn(snapshot);
        when(tournamentHooks.requiredWatchers(competition, match)).thenReturn(2);
        when(tournamentHooks.matchFullyWatched(competition, match)).thenReturn(true);
        when(viewRepository.countByMatchId(matchId)).thenReturn(2L);

        var response = commandService.openMatch(sessionId, matchId, homeUserId, true, CompetitionType.TOURNAMENT);

        assertThat(response.homeScore()).isEqualTo(3);
        assertThat(response.awayScore()).isEqualTo(2);
        assertThat(response.replay()).isTrue();
    }

    @Test
    void openMatch_wrongCompetitionType_throws404() {
        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commandService.openMatch(
            sessionId, matchId, homeUserId, false, CompetitionType.LEAGUE
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void openMatch_matchFromOtherCompetition_throws404() {
        CompetitionEntity competition = tournamentCompetition();
        CompetitionMatchEntity match = completedMatch();
        match.setCompetitionId(UUID.randomUUID());

        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(competition));
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(activeSession(homeUserId)));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> commandService.openMatch(
            sessionId, matchId, homeUserId, false, CompetitionType.TOURNAMENT
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).isEqualTo("Match not found"));
    }

    @Test
    void openMatch_nonMemberFromSnapshot_throws403() {
        CompetitionEntity competition = tournamentCompetition();
        CompetitionMatchEntity match = completedMatch();
        UUID outsider = UUID.randomUUID();

        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(competition));
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());
        when(tournamentHooks.readSnapshot(competition)).thenReturn(draftSnapshot());

        assertThatThrownBy(() -> commandService.openMatch(
            sessionId, matchId, outsider, false, CompetitionType.TOURNAMENT
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void acknowledgeWatch_recordsViewAndReturnsCompetition() {
        CompetitionEntity competition = tournamentCompetition();
        CompetitionMatchEntity match = completedMatch();
        CompetitionResponse expected = org.mockito.Mockito.mock(CompetitionResponse.class);

        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(competition));
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(activeSession(homeUserId)));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(tournamentHooks.isReplaysPhase(competition)).thenReturn(false);
        when(viewRepository.existsByMatchIdAndUserId(matchId, homeUserId)).thenReturn(false);
        when(tournamentHooks.afterWatchRecorded(sessionId, competition)).thenReturn(competition);
        when(competitionQueryService.getBySessionId(sessionId, homeUserId)).thenReturn(expected);

        var response = commandService.acknowledgeWatch(sessionId, matchId, homeUserId, CompetitionType.TOURNAMENT);

        assertThat(response).isSameAs(expected);
        ArgumentCaptor<dev.sportdraft.competition.entity.CompetitionMatchViewEntity> captor =
            ArgumentCaptor.forClass(dev.sportdraft.competition.entity.CompetitionMatchViewEntity.class);
        verify(viewRepository).save(captor.capture());
        assertThat(captor.getValue().getMatchId()).isEqualTo(matchId);
        assertThat(captor.getValue().getUserId()).isEqualTo(homeUserId);
    }

    @Test
    void acknowledgeWatch_pendingMatch_throws409() {
        CompetitionEntity competition = tournamentCompetition();
        CompetitionMatchEntity match = completedMatch();
        match.setStatus(CompetitionMatchStatus.PENDING);

        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(competition));
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(activeSession(homeUserId)));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> commandService.acknowledgeWatch(
            sessionId, matchId, homeUserId, CompetitionType.TOURNAMENT
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Match has not been simulated yet");
            });
        verify(viewRepository, never()).save(any());
    }

    @Test
    void acknowledgeWatch_replaysPhase_skipsRecording() {
        CompetitionEntity competition = tournamentCompetition();
        CompetitionMatchEntity match = completedMatch();
        CompetitionResponse expected = org.mockito.Mockito.mock(CompetitionResponse.class);

        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(competition));
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(activeSession(homeUserId)));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(tournamentHooks.isReplaysPhase(competition)).thenReturn(true);
        when(competitionQueryService.getBySessionId(sessionId, homeUserId)).thenReturn(expected);

        var response = commandService.acknowledgeWatch(sessionId, matchId, homeUserId, CompetitionType.TOURNAMENT);

        assertThat(response).isSameAs(expected);
        verify(viewRepository, never()).save(any());
        verify(tournamentHooks, never()).afterWatchRecorded(eq(sessionId), any());
    }

    private CompetitionEntity tournamentCompetition() {
        CompetitionEntity entity = new CompetitionEntity();
        entity.setId(competitionId);
        entity.setSessionId(sessionId);
        entity.setType(CompetitionType.TOURNAMENT);
        entity.setStatus(CompetitionStatus.IN_PROGRESS);
        return entity;
    }

    private CompetitionMatchEntity completedMatch() {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(matchId);
        match.setCompetitionId(competitionId);
        match.setRoundKey("SEMI_1");
        match.setHomeUserId(homeUserId);
        match.setAwayUserId(awayUserId);
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        match.setSeed(1L);
        match.setSortOrder(1);
        return match;
    }

    private CompetitionDraftSnapshot draftSnapshot() {
        return new CompetitionDraftSnapshot(
            sessionId,
            42L,
            List.of(
                new CompetitionDraftSnapshot.PlayerSnapshot(homeUserId, "Home", Formation.FOUR_FOUR_TWO, List.of()),
                new CompetitionDraftSnapshot.PlayerSnapshot(awayUserId, "Away", Formation.FOUR_FOUR_TWO, List.of())
            )
        );
    }

    private ActiveSession activeSession(UUID memberUserId) {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now());
        session.getPlayers().add(new ActivePlayer(
            UUID.randomUUID(), memberUserId, "Member", Formation.FOUR_FOUR_TWO, 100
        ));
        return session;
    }
}
