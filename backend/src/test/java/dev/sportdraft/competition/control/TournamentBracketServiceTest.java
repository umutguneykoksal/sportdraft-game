package dev.sportdraft.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import dev.sportdraft.competition.entity.TournamentPhase;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.simulation.control.CompetitionMatchSimulator;
import dev.sportdraft.simulation.control.DraftCompletedEvent;
import dev.sportdraft.simulation.entity.SimulationResult;
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
class TournamentBracketServiceTest {

    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private CompetitionMatchRepository matchRepository;
    @Mock
    private CompetitionMatchViewRepository viewRepository;
    @Mock
    private CompetitionMatchSimulator matchSimulator;
    @Mock
    private TournamentBracketFlow bracketFlow;

    private TournamentBracketService bracketService;
    private CompetitionJson competitionJson;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID p0 = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID p3 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        competitionJson = new CompetitionJson(new ObjectMapper().registerModule(new JavaTimeModule()));
        bracketService = new TournamentBracketService(
            competitionRepository,
            matchRepository,
            viewRepository,
            matchSimulator,
            competitionJson,
            bracketFlow
        );
    }

    @Test
    void initializeTournament_createsSemisForFourPlayers() {
        DraftCompletedEvent event = draftEvent();

        CompetitionEntity saved = bracketService.initializeTournament(event);

        assertThat(saved.getType()).isEqualTo(CompetitionType.TOURNAMENT);
        assertThat(saved.getStatus()).isEqualTo(CompetitionStatus.IN_PROGRESS);
        assertThat(saved.getTournamentPhase()).isEqualTo(TournamentPhase.SEMIS);
        verify(competitionRepository).save(saved);
        ArgumentCaptor<CompetitionMatchEntity> matchCaptor = ArgumentCaptor.forClass(CompetitionMatchEntity.class);
        verify(matchRepository, org.mockito.Mockito.times(2)).save(matchCaptor.capture());
        assertThat(matchCaptor.getAllValues())
            .extracting(CompetitionMatchEntity::getRoundKey)
            .containsExactlyInAnyOrder("SEMI_1", "SEMI_2");
    }

    @Test
    void initializeTournament_rejectsWrongPlayerCount() {
        DraftCompletedEvent event = new DraftCompletedEvent(
            UUID.randomUUID(),
            Instant.now(),
            sessionId,
            GameMode.TOURNAMENT,
            1L,
            List.of(player(p0), player(p1))
        );

        assertThatThrownBy(() -> bracketService.initializeTournament(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exactly 4 players");
    }

    @Test
    void simulateMatchIfNeeded_delegatesToSimulatorAndEnsuresFinal() {
        CompetitionEntity competition = savedCompetition();
        CompetitionMatchEntity match = semiMatch("SEMI_1", p0, p1);
        SimulationResult result = new SimulationResult(2, 1, List.of());

        when(matchSimulator.simulateIfNeeded(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(match), org.mockito.ArgumentMatchers.eq(false)))
            .thenReturn(result);

        SimulationResult actual = bracketService.simulateMatchIfNeeded(competition, match);

        assertThat(actual).isSameAs(result);
        verify(bracketFlow).ensureFinalMatch(competition);
    }

    @Test
    void isMatchPlayable_completedMatchWithoutViewer() {
        CompetitionEntity competition = savedCompetition();
        CompetitionMatchEntity match = semiMatch("SEMI_1", p0, p1);
        match.setStatus(CompetitionMatchStatus.COMPLETED);

        assertThat(bracketService.isMatchPlayable(competition, match)).isTrue();
    }

    @Test
    void requiredWatchers_semiRequiresTwo() {
        CompetitionEntity competition = savedCompetition();
        CompetitionMatchEntity match = semiMatch("SEMI_1", p0, p1);

        assertThat(bracketService.requiredWatchers(competition, match)).isEqualTo(2);
    }

    @Test
    void matchFullyWatched_whenViewCountMeetsRequirement() {
        CompetitionEntity competition = savedCompetition();
        CompetitionMatchEntity match = semiMatch("SEMI_1", p0, p1);
        when(viewRepository.countByMatchId(match.getId())).thenReturn(2L);

        assertThat(bracketService.matchFullyWatched(competition, match)).isTrue();
    }

    @Test
    void isTournamentParticipant_checksSnapshot() {
        CompetitionEntity competition = savedCompetition();
        UUID participant = p0;

        assertThat(bracketService.isTournamentParticipant(competition, participant)).isTrue();
        assertThat(bracketService.isTournamentParticipant(competition, UUID.randomUUID())).isFalse();
    }

    @Test
    void scoresVisibleForViewer_hiddenUntilReplayPhase() {
        CompetitionEntity competition = savedCompetition();
        CompetitionMatchEntity match = semiMatch("SEMI_1", p0, p1);
        match.setStatus(CompetitionMatchStatus.COMPLETED);

        assertThat(bracketService.scoresVisibleForViewer(competition, match, false)).isFalse();
    }

    @Test
    void isReplayAllowed_onlyInReplaysPhase() {
        CompetitionEntity competition = savedCompetition();
        CompetitionMatchEntity match = semiMatch("SEMI_1", p0, p1);
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        competition.setTournamentPhase(TournamentPhase.REPLAYS);

        assertThat(bracketService.isReplayAllowed(competition, match)).isTrue();
    }

    private DraftCompletedEvent draftEvent() {
        return new DraftCompletedEvent(
            UUID.randomUUID(),
            Instant.now(),
            sessionId,
            GameMode.TOURNAMENT,
            10L,
            List.of(player(p0), player(p1), player(p2), player(p3))
        );
    }

    private DraftCompletedEvent.DraftPlayerRoster player(UUID userId) {
        return new DraftCompletedEvent.DraftPlayerRoster(
            userId, "P-" + userId.toString().substring(0, 4), Formation.FOUR_FOUR_TWO, List.of()
        );
    }

    private CompetitionEntity savedCompetition() {
        CompetitionEntity competition = bracketService.initializeTournament(draftEvent());
        competition.setDraftSnapshotJson(competitionJson.writeSnapshot(
            CompetitionDraftSnapshot.from(draftEvent())
        ));
        return competition;
    }

    private CompetitionMatchEntity semiMatch(String roundKey, UUID home, UUID away) {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setCompetitionId(UUID.randomUUID());
        match.setRoundKey(roundKey);
        match.setHomeUserId(home);
        match.setAwayUserId(away);
        match.setStatus(CompetitionMatchStatus.PENDING);
        match.setSeed(1L);
        match.setSortOrder(1);
        return match;
    }
}
