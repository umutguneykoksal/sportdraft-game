package com.example.auction.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.competition.entity.CompetitionMatchViewRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.CompetitionType;
import com.example.auction.competition.entity.LeaguePhase;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.simulation.control.CompetitionMatchSimulator;
import com.example.auction.simulation.control.DraftCompletedEvent;
import com.example.auction.simulation.entity.SimulationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeagueFixtureServiceTest {

    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private CompetitionMatchRepository matchRepository;
    @Mock
    private CompetitionMatchViewRepository viewRepository;
    @Mock
    private CompetitionMatchSimulator matchSimulator;
    @Mock
    private LeaguePhaseCoordinator phaseCoordinator;

    private LeagueFixtureService fixtureService;
    private CompetitionJson competitionJson;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID p0 = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID p3 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        competitionJson = new CompetitionJson(new ObjectMapper().registerModule(new JavaTimeModule()));
        fixtureService = new LeagueFixtureService(
            competitionRepository,
            matchRepository,
            viewRepository,
            matchSimulator,
            competitionJson,
            phaseCoordinator
        );
    }

    @Test
    void initializeLeague_createsRoundRobinFixtures() {
        DraftCompletedEvent event = draftEvent();

        CompetitionEntity competition = fixtureService.initializeLeague(event);

        assertThat(competition.getType()).isEqualTo(CompetitionType.LEAGUE);
        assertThat(competition.getLeaguePhase()).isEqualTo(LeaguePhase.MATCHDAY_1);
        assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.IN_PROGRESS);
        verify(competitionRepository).save(competition);
        ArgumentCaptor<CompetitionMatchEntity> matchCaptor = ArgumentCaptor.forClass(CompetitionMatchEntity.class);
        verify(matchRepository, org.mockito.Mockito.times(6)).save(matchCaptor.capture());
        assertThat(matchCaptor.getAllValues())
            .extracting(CompetitionMatchEntity::getRoundKey)
            .containsExactly("MD1_A", "MD1_B", "MD2_A", "MD2_B", "MD3_A", "MD3_B");
    }

    @Test
    void initializeLeague_rejectsWrongPlayerCount() {
        DraftCompletedEvent event = new DraftCompletedEvent(
            UUID.randomUUID(),
            Instant.now(),
            sessionId,
            GameMode.LEAGUE,
            1L,
            List.of(player(p0), player(p1))
        );

        assertThatThrownBy(() -> fixtureService.initializeLeague(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exactly 4 players");
    }

    @Test
    void simulateMatchIfNeeded_delegatesToSimulator() {
        CompetitionEntity competition = fixtureService.initializeLeague(draftEvent());
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setRoundKey("MD1_A");
        match.setStatus(CompetitionMatchStatus.PENDING);
        SimulationResult result = new SimulationResult(1, 0, List.of());

        when(matchSimulator.simulateIfNeeded(any(), eq(match), eq(true))).thenReturn(result);

        SimulationResult actual = fixtureService.simulateMatchIfNeeded(competition, match);

        assertThat(actual).isSameAs(result);
        verify(matchSimulator).simulateIfNeeded(any(), eq(match), eq(true));
    }

    @Test
    void readSnapshot_deserializesDraftJson() {
        CompetitionEntity competition = fixtureService.initializeLeague(draftEvent());

        CompetitionDraftSnapshot snapshot = fixtureService.readSnapshot(competition);

        assertThat(snapshot.sessionId()).isEqualTo(sessionId);
        assertThat(snapshot.players()).hasSize(4);
    }

    @Test
    void isMandatoryWatchPending_trueForActiveMatchdayParticipant() {
        CompetitionEntity competition = fixtureService.initializeLeague(draftEvent());
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setRoundKey("MD1_A");
        match.setHomeUserId(p0);
        match.setAwayUserId(p1);
        match.setStatus(CompetitionMatchStatus.PENDING);

        assertThat(fixtureService.isMandatoryWatchPending(competition, match, p0, false)).isTrue();
        assertThat(fixtureService.isReplayAllowed(competition, match)).isFalse();
    }

    @Test
    void matchFullyWatched_requiresTwoViews() {
        CompetitionEntity competition = fixtureService.initializeLeague(draftEvent());
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setRoundKey("MD1_A");
        when(viewRepository.countByMatchId(match.getId())).thenReturn(2L);

        assertThat(fixtureService.matchFullyWatched(competition, match)).isTrue();
    }

    private DraftCompletedEvent draftEvent() {
        return new DraftCompletedEvent(
            UUID.randomUUID(),
            Instant.now(),
            sessionId,
            GameMode.LEAGUE,
            20L,
            List.of(player(p0), player(p1), player(p2), player(p3))
        );
    }

    private DraftCompletedEvent.DraftPlayerRoster player(UUID userId) {
        return new DraftCompletedEvent.DraftPlayerRoster(
            userId, "P-" + userId.toString().substring(0, 4), Formation.FOUR_FOUR_TWO, List.of()
        );
    }
}
