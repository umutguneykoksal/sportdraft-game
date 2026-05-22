package com.example.auction.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
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
import com.example.auction.competition.entity.TournamentPhase;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.control.SessionFinalizedEvent;
import java.time.Instant;
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
class CompetitionQueryServiceTest {

    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private CompetitionMatchRepository matchRepository;
    @Mock
    private CompetitionMatchViewRepository viewRepository;
    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private TournamentBracketService bracketService;
    @Mock
    private LeagueFixtureService leagueFixtureService;
    @Mock
    private LeagueStandingsService leagueStandingsService;
    @Mock
    private TournamentCompetitionRecoveryService tournamentRecoveryService;
    @Mock
    private LeagueCompetitionRecoveryService leagueRecoveryService;

    private CompetitionQueryService queryService;
    private LeagueVisibilityPolicy leagueVisibilityPolicy;
    private TournamentVisibilityPolicy tournamentVisibilityPolicy;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID competitionId = UUID.randomUUID();
    private final UUID homeUserId = UUID.randomUUID();
    private final UUID awayUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leagueVisibilityPolicy = new LeagueVisibilityPolicy();
        tournamentVisibilityPolicy = new TournamentVisibilityPolicy(bracketService);
        queryService = new CompetitionQueryService(
            competitionRepository,
            matchRepository,
            viewRepository,
            sessionStore,
            bracketService,
            leagueFixtureService,
            leagueStandingsService,
            tournamentRecoveryService,
            leagueRecoveryService,
            leagueVisibilityPolicy,
            tournamentVisibilityPolicy
        );
    }

    @Test
    void getBySessionId_notFound_throws404() {
        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getBySessionId(sessionId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void getBySessionId_tournament_returnsTournamentResponse() {
        CompetitionEntity competition = tournamentCompetition();
        CompetitionMatchEntity semi = match("SEMI_1");
        CompetitionDraftSnapshot snapshot = draftSnapshot();
        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(competition));
        when(matchRepository.findByCompetitionIdOrderBySortOrderAsc(competitionId)).thenReturn(List.of(semi));
        when(bracketService.readSnapshot(competition)).thenReturn(snapshot);
        lenient().when(bracketService.snapshotPlayerCount(competition)).thenReturn(2);
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());
        when(viewRepository.existsByMatchIdAndUserId(semi.getId(), homeUserId)).thenReturn(false);
        when(viewRepository.countByMatchId(semi.getId())).thenReturn(0L);

        var response = queryService.getBySessionId(sessionId, homeUserId);

        assertThat(response.type()).isEqualTo(CompetitionType.TOURNAMENT.name());
        assertThat(response.tournamentPhase()).isEqualTo(TournamentPhase.SEMIS.name());
        assertThat(response.matches()).hasSize(1);
        assertThat(response.leaguePhase()).isNull();
    }

    @Test
    void getBySessionId_league_returnsLeagueResponseWithStandings() {
        CompetitionEntity competition = leagueCompetition();
        CompetitionMatchEntity fixture = match("MD1_1");
        CompetitionDraftSnapshot snapshot = draftSnapshot();
        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(competition));
        when(matchRepository.findByCompetitionIdOrderBySortOrderAsc(competitionId)).thenReturn(List.of(fixture));
        when(leagueFixtureService.readSnapshot(competition)).thenReturn(snapshot);
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());
        when(leagueStandingsService.computeStandings(snapshot, List.of(fixture))).thenReturn(List.of());
        when(viewRepository.existsByMatchIdAndUserId(fixture.getId(), homeUserId)).thenReturn(false);
        when(viewRepository.countByMatchId(fixture.getId())).thenReturn(0L);

        var response = queryService.getBySessionId(sessionId, homeUserId);

        assertThat(response.type()).isEqualTo(CompetitionType.LEAGUE.name());
        assertThat(response.leaguePhase()).isEqualTo(LeaguePhase.MATCHDAY_1.name());
        assertThat(response.standings()).isEmpty();
    }

    @Test
    void getBySessionId_triggersRecoveryWhenMissing() {
        CompetitionEntity competition = tournamentCompetition();
        when(competitionRepository.findBySessionId(sessionId))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(competition));
        when(matchRepository.findByCompetitionIdOrderBySortOrderAsc(competitionId)).thenReturn(List.of());
        when(bracketService.readSnapshot(competition)).thenReturn(draftSnapshot());
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());

        var response = queryService.getBySessionId(sessionId);

        assertThat(response.competitionId()).isEqualTo(competitionId);
    }

    private CompetitionEntity tournamentCompetition() {
        CompetitionEntity entity = new CompetitionEntity();
        entity.setId(competitionId);
        entity.setSessionId(sessionId);
        entity.setType(CompetitionType.TOURNAMENT);
        entity.setStatus(CompetitionStatus.IN_PROGRESS);
        entity.setTournamentPhase(TournamentPhase.SEMIS);
        return entity;
    }

    private CompetitionEntity leagueCompetition() {
        CompetitionEntity entity = new CompetitionEntity();
        entity.setId(competitionId);
        entity.setSessionId(sessionId);
        entity.setType(CompetitionType.LEAGUE);
        entity.setStatus(CompetitionStatus.IN_PROGRESS);
        entity.setLeaguePhase(LeaguePhase.MATCHDAY_1);
        return entity;
    }

    private CompetitionMatchEntity match(String roundKey) {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setCompetitionId(competitionId);
        match.setRoundKey(roundKey);
        match.setHomeUserId(homeUserId);
        match.setAwayUserId(awayUserId);
        match.setStatus(CompetitionMatchStatus.PENDING);
        match.setSeed(1L);
        match.setSortOrder(1);
        return match;
    }

    private CompetitionDraftSnapshot draftSnapshot() {
        return new CompetitionDraftSnapshot(
            sessionId,
            42L,
            List.of(
                new CompetitionDraftSnapshot.PlayerSnapshot(
                    homeUserId, "Home", Formation.FOUR_FOUR_TWO, List.of()
                ),
                new CompetitionDraftSnapshot.PlayerSnapshot(
                    awayUserId, "Away", Formation.FOUR_FOUR_TWO, List.of()
                )
            )
        );
    }
}
