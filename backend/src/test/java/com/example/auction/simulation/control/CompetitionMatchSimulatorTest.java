package com.example.auction.simulation.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.competition.control.CompetitionDraftSnapshot;
import com.example.auction.competition.control.CompetitionJson;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.bidding.entity.PlayerAttributes;
import com.example.auction.bidding.entity.Position;
import com.example.auction.session.entity.Formation;
import com.example.auction.simulation.entity.MatchEvent;
import com.example.auction.simulation.entity.SimulationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitionMatchSimulatorTest {

    @Mock
    private CompetitionMatchRepository matchRepository;

    private CompetitionMatchSimulator simulator;
    private CompetitionJson competitionJson;

    private final UUID homeUserId = UUID.randomUUID();
    private final UUID awayUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        competitionJson = new CompetitionJson(new ObjectMapper().registerModule(new JavaTimeModule()));
        simulator = new CompetitionMatchSimulator(new MatchSimulator(), competitionJson, matchRepository);
    }

    @Test
    void simulateIfNeeded_persistsResultForPendingMatch() {
        CompetitionMatchEntity match = pendingMatch();
        CompetitionDraftSnapshot snapshot = snapshotWithRosters();

        SimulationResult result = simulator.simulateIfNeeded(snapshot, match, true);

        assertThat(result.homeGoals()).isGreaterThanOrEqualTo(0);
        assertThat(match.getStatus()).isEqualTo(CompetitionMatchStatus.COMPLETED);
        assertThat(match.getHomeScore()).isEqualTo(result.homeGoals());
        assertThat(match.getReplayDataJson()).isNotBlank();
        verify(matchRepository).save(match);
    }

    @Test
    void simulateIfNeeded_returnsStoredResultForCompletedMatch() {
        CompetitionMatchEntity match = pendingMatch();
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        match.setHomeScore(2);
        match.setAwayScore(1);
        match.setReplayDataJson(competitionJson.writeTimeline(List.of(
            MatchEvent.forPlayer(10, com.example.auction.simulation.entity.MatchEventType.GOAL,
                homeUserId, UUID.randomUUID(), "Striker", "Goal")
        )));
        CompetitionDraftSnapshot snapshot = snapshotWithRosters();

        SimulationResult result = simulator.simulateIfNeeded(snapshot, match, true);

        assertThat(result.homeGoals()).isEqualTo(2);
        assertThat(result.awayGoals()).isEqualTo(1);
        assertThat(result.timeline()).hasSize(1);
        verify(matchRepository, never()).save(any());
    }

    @Test
    void simulateIfNeeded_knockoutResolvesWinnerWhenDrawsDisallowed() {
        CompetitionMatchEntity match = pendingMatch();
        CompetitionDraftSnapshot snapshot = snapshotWithRosters();

        simulator.simulateIfNeeded(snapshot, match, false);

        ArgumentCaptor<CompetitionMatchEntity> captor = ArgumentCaptor.forClass(CompetitionMatchEntity.class);
        verify(matchRepository).save(captor.capture());
        assertThat(captor.getValue().getWinnerUserId()).isNotNull();
    }

    private CompetitionMatchEntity pendingMatch() {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setHomeUserId(homeUserId);
        match.setAwayUserId(awayUserId);
        match.setStatus(CompetitionMatchStatus.PENDING);
        match.setSeed(42L);
        return match;
    }

    private CompetitionDraftSnapshot snapshotWithRosters() {
        return new CompetitionDraftSnapshot(
            UUID.randomUUID(),
            42L,
            List.of(
                new CompetitionDraftSnapshot.PlayerSnapshot(
                    homeUserId, "Home", Formation.FOUR_FOUR_TWO,
                    List.of(ratedCard("Home Striker"))
                ),
                new CompetitionDraftSnapshot.PlayerSnapshot(
                    awayUserId, "Away", Formation.FOUR_FOUR_TWO,
                    List.of(ratedCard("Away Striker"))
                )
            )
        );
    }

    private SessionFinalizedEvent.RatedCard ratedCard(String name) {
        return new SessionFinalizedEvent.RatedCard(
            UUID.randomUUID(),
            Position.FWD,
            80,
            name,
            new PlayerAttributes(80, 80, 80, 80, 80)
        );
    }
}
