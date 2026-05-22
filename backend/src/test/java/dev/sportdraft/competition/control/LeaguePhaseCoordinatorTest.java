package dev.sportdraft.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.CompetitionMatchViewRepository;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionStatus;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.competition.entity.LeaguePhase;
import dev.sportdraft.session.entity.Formation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaguePhaseCoordinatorTest {

    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private CompetitionMatchRepository matchRepository;
    @Mock
    private CompetitionMatchViewRepository viewRepository;
    private LeagueStandingsService standingsService;
    @Mock
    private CompetitionJson competitionJson;

    private LeaguePhaseCoordinator coordinator;

    private final UUID competitionId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID p0 = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID p3 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        standingsService = new LeagueStandingsService();
        coordinator = new LeaguePhaseCoordinator(
            competitionRepository,
            matchRepository,
            viewRepository,
            standingsService,
            competitionJson
        );
    }

    @Test
    void tryResolveAfterRoundRobin_singleChampionFinalizesLeague() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.MATCHDAY_3);
        List<CompetitionMatchEntity> matches = List.of(
            completedMatch("MD1_A", p0, p1, 3, 0),
            completedMatch("MD1_B", p2, p3, 1, 1),
            completedMatch("MD2_A", p0, p2, 2, 0),
            completedMatch("MD2_B", p1, p3, 2, 1),
            completedMatch("MD3_A", p0, p3, 1, 0),
            completedMatch("MD3_B", p1, p2, 0, 1)
        );
        CompetitionDraftSnapshot snapshot = new CompetitionDraftSnapshot(
            sessionId,
            42L,
            List.of(
                new CompetitionDraftSnapshot.PlayerSnapshot(p0, "P0", Formation.FOUR_FOUR_TWO, List.of()),
                new CompetitionDraftSnapshot.PlayerSnapshot(p1, "P1", Formation.FOUR_FOUR_TWO, List.of()),
                new CompetitionDraftSnapshot.PlayerSnapshot(p2, "P2", Formation.FOUR_FOUR_TWO, List.of()),
                new CompetitionDraftSnapshot.PlayerSnapshot(p3, "P3", Formation.FOUR_FOUR_TWO, List.of())
            )
        );

        when(matchRepository.findByCompetitionIdOrderBySortOrderAsc(competitionId)).thenReturn(matches);
        when(competitionJson.readSnapshot(competition.getDraftSnapshotJson())).thenReturn(snapshot);
        stubWatched(matches.get(4));
        stubWatched(matches.get(5));

        assertThat(coordinator.tryResolveAfterRoundRobin(competition)).isTrue();
        assertThat(competition.getChampionUserId()).isEqualTo(p0);
        assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.COMPLETED);
    }

    @Test
    void tryStartMatchdayBreak_advancesAfterMatchdayCompleteAndWatched() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.MATCHDAY_1);
        CompetitionMatchEntity md1A = completedMatch("MD1_1", p0, p1);
        CompetitionMatchEntity md1B = completedMatch("MD1_2", p2, p3);

        when(matchRepository.findByCompetitionIdOrderBySortOrderAsc(competitionId))
            .thenReturn(List.of(md1A, md1B));
        stubWatched(md1A);
        stubWatched(md1B);

        assertThat(coordinator.tryStartMatchdayBreak(competition)).isTrue();
        assertThat(competition.getLeaguePhase()).isEqualTo(LeaguePhase.BREAK_AFTER_MD1);
        assertThat(competition.getIntermissionEndsAt()).isNotNull();
        verify(competitionRepository).save(competition);
    }

    @Test
    void tryStartMatchdayBreak_returnsFalseWhenMatchesIncomplete() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.MATCHDAY_1);
        CompetitionMatchEntity md1A = completedMatch("MD1_1", p0, p1);
        CompetitionMatchEntity md1B = pendingMatch("MD1_2", p2, p3);

        when(matchRepository.findByCompetitionIdOrderBySortOrderAsc(competitionId))
            .thenReturn(List.of(md1A, md1B));

        assertThat(coordinator.tryStartMatchdayBreak(competition)).isFalse();
    }

    @Test
    void tryAdvanceFromBreak_movesToNextMatchdayWhenIntermissionElapsed() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.BREAK_AFTER_MD1);
        competition.setIntermissionEndsAt(Instant.now().minusSeconds(1));

        assertThat(coordinator.tryAdvanceFromBreak(competition)).isTrue();
        assertThat(competition.getLeaguePhase()).isEqualTo(LeaguePhase.MATCHDAY_2);
        assertThat(competition.getIntermissionEndsAt()).isNull();
        verify(competitionRepository).save(competition);
    }

    @Test
    void tryAdvanceFromBreak_returnsFalseBeforeIntermissionEnds() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.BREAK_AFTER_MD1);
        competition.setIntermissionEndsAt(Instant.now().plusSeconds(60));

        assertThat(coordinator.tryAdvanceFromBreak(competition)).isFalse();
    }

    @Test
    void createPlayoffFinal_createsMatchAndUpdatesPhase() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.MATCHDAY_3);

        coordinator.createPlayoffFinal(competition, p0, p1, 99L);

        verify(matchRepository).save(org.mockito.ArgumentMatchers.argThat(match ->
            LeagueRules.PLAYOFF_ROUND_KEY.equals(match.getRoundKey())
                && match.getHomeUserId().equals(p0)
                && match.getAwayUserId().equals(p1)
        ));
        assertThat(competition.getLeaguePhase()).isEqualTo(LeaguePhase.CHAMPIONSHIP_PLAYOFF);
        verify(competitionRepository).save(competition);
    }

    @Test
    void createPlayoffFinal_isIdempotentWhenPlayoffExists() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.MATCHDAY_3);
        when(matchRepository.findByCompetitionIdAndRoundKey(competitionId, LeagueRules.PLAYOFF_ROUND_KEY))
            .thenReturn(Optional.of(completedMatch(LeagueRules.PLAYOFF_ROUND_KEY, p0, p1)));

        coordinator.createPlayoffFinal(competition, p0, p1, 99L);

        verify(matchRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tryCompletePlayoff_marksChampionWhenPlayoffWatched() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.CHAMPIONSHIP_PLAYOFF);
        CompetitionMatchEntity playoff = completedMatch(LeagueRules.PLAYOFF_ROUND_KEY, p0, p1);
        playoff.setWinnerUserId(p0);

        when(matchRepository.findByCompetitionIdAndRoundKey(competitionId, LeagueRules.PLAYOFF_ROUND_KEY))
            .thenReturn(Optional.of(playoff));
        stubWatched(playoff);
        when(competitionRepository.save(competition)).thenReturn(competition);

        CompetitionEntity result = coordinator.tryCompletePlayoff(competition);

        assertThat(result.getStatus()).isEqualTo(CompetitionStatus.COMPLETED);
        assertThat(result.getLeaguePhase()).isEqualTo(LeaguePhase.REPLAYS);
        assertThat(result.getChampionUserId()).isEqualTo(p0);
    }

    private CompetitionEntity leagueCompetition(LeaguePhase phase) {
        CompetitionEntity entity = new CompetitionEntity();
        entity.setId(competitionId);
        entity.setSessionId(sessionId);
        entity.setType(CompetitionType.LEAGUE);
        entity.setStatus(CompetitionStatus.IN_PROGRESS);
        entity.setLeaguePhase(phase);
        entity.setDraftSnapshotJson("{}");
        return entity;
    }

    private CompetitionMatchEntity completedMatch(String roundKey, UUID home, UUID away) {
        return completedMatch(roundKey, home, away, 1, 0);
    }

    private CompetitionMatchEntity completedMatch(
        String roundKey, UUID home, UUID away, int homeScore, int awayScore
    ) {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setCompetitionId(competitionId);
        match.setRoundKey(roundKey);
        match.setHomeUserId(home);
        match.setAwayUserId(away);
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setSeed(1L);
        match.setSortOrder(1);
        return match;
    }

    private CompetitionMatchEntity pendingMatch(String roundKey, UUID home, UUID away) {
        CompetitionMatchEntity match = completedMatch(roundKey, home, away);
        match.setStatus(CompetitionMatchStatus.PENDING);
        match.setHomeScore(null);
        match.setAwayScore(null);
        return match;
    }

    private void stubWatched(CompetitionMatchEntity match) {
        when(viewRepository.existsByMatchIdAndUserId(match.getId(), match.getHomeUserId())).thenReturn(true);
        when(viewRepository.existsByMatchIdAndUserId(match.getId(), match.getAwayUserId())).thenReturn(true);
    }
}
