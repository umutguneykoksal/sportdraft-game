package com.example.auction.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Mock
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
        coordinator = new LeaguePhaseCoordinator(
            competitionRepository,
            matchRepository,
            viewRepository,
            standingsService,
            competitionJson
        );
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
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setCompetitionId(competitionId);
        match.setRoundKey(roundKey);
        match.setHomeUserId(home);
        match.setAwayUserId(away);
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        match.setHomeScore(1);
        match.setAwayScore(0);
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
