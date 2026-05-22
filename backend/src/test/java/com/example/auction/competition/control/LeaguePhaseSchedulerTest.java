package com.example.auction.competition.control;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.CompetitionType;
import com.example.auction.competition.entity.LeaguePhase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaguePhaseSchedulerTest {

    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private LeagueFixtureService leagueService;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private LeaguePhaseScheduler scheduler;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID competitionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scheduler = new LeaguePhaseScheduler(competitionRepository, leagueService, sseEmitterRegistry);
    }

    @Test
    void advanceMatchdayBreaks_broadcastsWhenBreakEnds() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.BREAK_AFTER_MD1);
        competition.setIntermissionEndsAt(Instant.now().minusSeconds(1));

        when(competitionRepository.findByStatus(CompetitionStatus.IN_PROGRESS)).thenReturn(List.of(competition));
        when(leagueService.tryAdvanceFromBreak(competition)).thenReturn(true);

        scheduler.advanceMatchdayBreaks();

        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("league_matchday_started"), org.mockito.ArgumentMatchers.any());
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("league_updated"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void advanceMatchdayBreaks_skipsNonLeagueCompetitions() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.BREAK_AFTER_MD1);
        competition.setType(CompetitionType.TOURNAMENT);
        when(competitionRepository.findByStatus(CompetitionStatus.IN_PROGRESS)).thenReturn(List.of(competition));

        scheduler.advanceMatchdayBreaks();

        verify(leagueService, never()).tryAdvanceFromBreak(org.mockito.ArgumentMatchers.any());
    }

    private CompetitionEntity leagueCompetition(LeaguePhase phase) {
        CompetitionEntity entity = new CompetitionEntity();
        entity.setId(competitionId);
        entity.setSessionId(sessionId);
        entity.setType(CompetitionType.LEAGUE);
        entity.setStatus(CompetitionStatus.IN_PROGRESS);
        entity.setLeaguePhase(phase);
        return entity;
    }
}
