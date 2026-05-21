package com.example.auction.competition.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.session.entity.Formation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeagueStandingsServiceTest {

    private LeagueStandingsService service;
    private UUID p0;
    private UUID p1;
    private UUID p2;
    private UUID p3;
    private TournamentDraftSnapshot snapshot;

    @BeforeEach
    void setUp() {
        service = new LeagueStandingsService();
        p0 = UUID.randomUUID();
        p1 = UUID.randomUUID();
        p2 = UUID.randomUUID();
        p3 = UUID.randomUUID();
        snapshot = new TournamentDraftSnapshot(
            UUID.randomUUID(),
            1L,
            List.of(
                player(p0, "P0"),
                player(p1, "P1"),
                player(p2, "P2"),
                player(p3, "P3")
            )
        );
    }

    @Test
    void resolveChampionship_coChampionsWhenFourWayTie() {
        List<CompetitionMatchEntity> matches = List.of(
            completed("MD1_A", p0, p1, 1, 1),
            completed("MD1_B", p2, p3, 1, 1),
            completed("MD2_A", p0, p2, 1, 1),
            completed("MD2_B", p1, p3, 1, 1),
            completed("MD3_A", p0, p3, 1, 1),
            completed("MD3_B", p1, p2, 1, 1)
        );

        var resolution = service.resolveChampionship(snapshot, matches);

        assertThat(resolution).isInstanceOf(LeagueStandingsService.ChampionshipResolution.CoChampions.class);
        assertThat(((LeagueStandingsService.ChampionshipResolution.CoChampions) resolution).userIds()).hasSize(4);
    }

    @Test
    void resolveChampionship_singleWinnerWhenClearLeader() {
        List<CompetitionMatchEntity> matches = List.of(
            completed("MD1_A", p0, p1, 3, 0),
            completed("MD1_B", p2, p3, 1, 1),
            completed("MD2_A", p0, p2, 2, 0),
            completed("MD2_B", p1, p3, 2, 1),
            completed("MD3_A", p0, p3, 1, 0),
            completed("MD3_B", p1, p2, 0, 1)
        );

        var resolution = service.resolveChampionship(snapshot, matches);

        assertThat(resolution).isInstanceOf(LeagueStandingsService.ChampionshipResolution.SingleChampion.class);
        assertThat(((LeagueStandingsService.ChampionshipResolution.SingleChampion) resolution).userId()).isEqualTo(p0);
    }

    @Test
    void computeStandings_awardsThreeOneZero() {
        List<CompetitionMatchEntity> matches = List.of(
            completed("MD1_A", p0, p1, 2, 0)
        );

        var standings = service.computeStandings(snapshot, matches);
        var leader = standings.stream().filter(s -> s.userId().equals(p0)).findFirst().orElseThrow();

        assertThat(leader.points()).isEqualTo(3);
        assertThat(leader.won()).isEqualTo(1);
        assertThat(leader.goalsFor()).isEqualTo(2);
    }

    private static TournamentDraftSnapshot.PlayerSnapshot player(UUID id, String nick) {
        return new TournamentDraftSnapshot.PlayerSnapshot(
            id,
            nick,
            Formation.FOUR_FOUR_TWO,
            List.of()
        );
    }

    private static CompetitionMatchEntity completed(
        String roundKey, UUID home, UUID away, int homeScore, int awayScore
    ) {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setRoundKey(roundKey);
        match.setHomeUserId(home);
        match.setAwayUserId(away);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        match.setSeed(1L);
        match.setSortOrder(1);
        if (homeScore > awayScore) {
            match.setWinnerUserId(home);
        } else if (awayScore > homeScore) {
            match.setWinnerUserId(away);
        }
        return match;
    }
}
