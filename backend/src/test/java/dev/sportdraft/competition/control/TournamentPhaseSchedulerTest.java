package dev.sportdraft.competition.control;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionStatus;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.competition.entity.TournamentPhase;
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
class TournamentPhaseSchedulerTest {

    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private CompetitionMatchRepository matchRepository;
    @Mock
    private TournamentBracketService bracketService;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private TournamentPhaseScheduler scheduler;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID competitionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scheduler = new TournamentPhaseScheduler(
            competitionRepository,
            matchRepository,
            bracketService,
            sseEmitterRegistry
        );
    }

    @Test
    void advanceIntermissionToFinal_broadcastsWhenFinalPhaseStarts() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.INTERMISSION);
        CompetitionMatchEntity finalMatch = finalMatch();

        when(competitionRepository.findByStatus(CompetitionStatus.IN_PROGRESS)).thenReturn(List.of(competition));
        when(bracketService.tryStartFinalPhase(competition)).thenReturn(true);
        when(matchRepository.findByCompetitionIdAndRoundKey(competitionId, "FINAL"))
            .thenReturn(Optional.of(finalMatch));

        scheduler.advanceIntermissionToFinal();

        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("tournament_final_start"), org.mockito.ArgumentMatchers.any());
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("tournament_updated"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void advanceIntermissionToFinal_skipsNonIntermissionCompetitions() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.SEMIS);
        when(competitionRepository.findByStatus(CompetitionStatus.IN_PROGRESS)).thenReturn(List.of(competition));

        scheduler.advanceIntermissionToFinal();

        verify(bracketService, never()).tryStartFinalPhase(org.mockito.ArgumentMatchers.any());
    }

    private CompetitionEntity tournamentCompetition(TournamentPhase phase) {
        CompetitionEntity entity = new CompetitionEntity();
        entity.setId(competitionId);
        entity.setSessionId(sessionId);
        entity.setType(CompetitionType.TOURNAMENT);
        entity.setStatus(CompetitionStatus.IN_PROGRESS);
        entity.setTournamentPhase(phase);
        entity.setIntermissionEndsAt(Instant.now().minusSeconds(1));
        return entity;
    }

    private CompetitionMatchEntity finalMatch() {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setCompetitionId(competitionId);
        match.setRoundKey("FINAL");
        match.setStatus(CompetitionMatchStatus.PENDING);
        return match;
    }
}
