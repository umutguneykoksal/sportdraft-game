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
import dev.sportdraft.competition.entity.TournamentPhase;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.simulation.control.SessionFinalizedEvent;
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

@ExtendWith(MockitoExtension.class)
class TournamentBracketFlowTest {

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private CompetitionMatchRepository matchRepository;

    @Mock
    private CompetitionMatchViewRepository viewRepository;

    @Mock
    private CompetitionJson competitionJson;

    private TournamentBracketFlow coordinator;

    private final UUID competitionId = UUID.randomUUID();
    private final UUID homeUserId = UUID.randomUUID();
    private final UUID awayUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        coordinator = new TournamentBracketFlow(
            competitionRepository,
            matchRepository,
            viewRepository,
            competitionJson
        );
    }

    @Test
    void tryStartIntermissionIfReady_advancesWhenBothSemisCompleteAndWatched() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.SEMIS);
        CompetitionMatchEntity semi1 = completedSemi("SEMI_1", homeUserId, awayUserId);
        CompetitionMatchEntity semi2 = completedSemi("SEMI_2", UUID.randomUUID(), UUID.randomUUID());

        when(matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_1")).thenReturn(Optional.of(semi1));
        when(matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_2")).thenReturn(Optional.of(semi2));
        when(matchRepository.findByCompetitionIdAndRoundKey(competitionId, "FINAL")).thenReturn(Optional.empty());
        when(viewRepository.existsByMatchIdAndUserId(semi1.getId(), semi1.getHomeUserId())).thenReturn(true);
        when(viewRepository.existsByMatchIdAndUserId(semi1.getId(), semi1.getAwayUserId())).thenReturn(true);
        when(viewRepository.existsByMatchIdAndUserId(semi2.getId(), semi2.getHomeUserId())).thenReturn(true);
        when(viewRepository.existsByMatchIdAndUserId(semi2.getId(), semi2.getAwayUserId())).thenReturn(true);
        when(competitionJson.readSnapshot(competition.getDraftSnapshotJson())).thenReturn(snapshot());

        assertThat(coordinator.tryStartIntermissionIfReady(competition)).isTrue();
        assertThat(competition.getTournamentPhase()).isEqualTo(TournamentPhase.INTERMISSION);
        assertThat(competition.getIntermissionEndsAt()).isNotNull();
        verify(competitionRepository).save(competition);
        verify(matchRepository).save(org.mockito.ArgumentMatchers.any(CompetitionMatchEntity.class));
    }

    @Test
    void tryStartIntermissionIfReady_rejectsWrongPhase() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.INTERMISSION);

        assertThat(coordinator.tryStartIntermissionIfReady(competition)).isFalse();
    }

    @Test
    void tryStartFinalPhase_advancesWhenIntermissionExpired() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.INTERMISSION);
        competition.setIntermissionEndsAt(Instant.now().minusSeconds(1));

        assertThat(coordinator.tryStartFinalPhase(competition)).isTrue();
        assertThat(competition.getTournamentPhase()).isEqualTo(TournamentPhase.FINAL);
        assertThat(competition.getIntermissionEndsAt()).isNull();
        verify(competitionRepository).save(competition);
    }

    @Test
    void tryCompleteTournament_marksCompletedWhenFinalWatched() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.FINAL);
        CompetitionMatchEntity finalMatch = completedSemi("FINAL", homeUserId, awayUserId);

        when(matchRepository.findByCompetitionIdAndRoundKey(competitionId, "FINAL")).thenReturn(Optional.of(finalMatch));
        when(viewRepository.countByMatchId(finalMatch.getId())).thenReturn(4L);
        when(competitionJson.readSnapshot(competition.getDraftSnapshotJson())).thenReturn(snapshot());
        when(competitionRepository.save(competition)).thenReturn(competition);

        CompetitionEntity result = coordinator.tryCompleteTournament(competition);

        assertThat(result.getStatus()).isEqualTo(CompetitionStatus.COMPLETED);
        assertThat(result.getTournamentPhase()).isEqualTo(TournamentPhase.REPLAYS);
        assertThat(result.getChampionUserId()).isEqualTo(homeUserId);
    }

    @Test
    void ensureFinalMatch_createsFinalWhenSemisResolved() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.SEMIS);
        CompetitionMatchEntity semi1 = completedSemi("SEMI_1", homeUserId, awayUserId);
        CompetitionMatchEntity semi2 = completedSemi("SEMI_2", UUID.randomUUID(), UUID.randomUUID());

        when(matchRepository.findByCompetitionIdAndRoundKey(competitionId, "FINAL")).thenReturn(Optional.empty());
        when(matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_1")).thenReturn(Optional.of(semi1));
        when(matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_2")).thenReturn(Optional.of(semi2));
        when(competitionJson.readSnapshot(competition.getDraftSnapshotJson())).thenReturn(snapshot());

        coordinator.ensureFinalMatch(competition);

        ArgumentCaptor<CompetitionMatchEntity> captor = ArgumentCaptor.forClass(CompetitionMatchEntity.class);
        verify(matchRepository).save(captor.capture());
        assertThat(captor.getValue().getRoundKey()).isEqualTo("FINAL");
        assertThat(captor.getValue().getHomeUserId()).isEqualTo(homeUserId);
    }

    private CompetitionEntity tournamentCompetition(TournamentPhase phase) {
        CompetitionEntity competition = new CompetitionEntity();
        competition.setId(competitionId);
        competition.setSessionId(UUID.randomUUID());
        competition.setTournamentPhase(phase);
        competition.setDraftSnapshotJson("{}");
        return competition;
    }

    private CompetitionMatchEntity completedSemi(String roundKey, UUID home, UUID away) {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setCompetitionId(competitionId);
        match.setRoundKey(roundKey);
        match.setHomeUserId(home);
        match.setAwayUserId(away);
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        match.setWinnerUserId(home);
        return match;
    }

    private CompetitionDraftSnapshot snapshot() {
        return new CompetitionDraftSnapshot(
            UUID.randomUUID(),
            100L,
            List.of(
                new CompetitionDraftSnapshot.PlayerSnapshot(
                    UUID.randomUUID(), "P1", Formation.FOUR_FOUR_TWO, List.of()
                ),
                new CompetitionDraftSnapshot.PlayerSnapshot(
                    UUID.randomUUID(), "P2", Formation.FOUR_FOUR_TWO, List.of()
                ),
                new CompetitionDraftSnapshot.PlayerSnapshot(
                    UUID.randomUUID(), "P3", Formation.FOUR_FOUR_TWO, List.of()
                ),
                new CompetitionDraftSnapshot.PlayerSnapshot(
                    UUID.randomUUID(), "P4", Formation.FOUR_FOUR_TWO, List.of()
                )
            )
        );
    }
}
