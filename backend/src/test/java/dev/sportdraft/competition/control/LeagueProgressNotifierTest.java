package dev.sportdraft.competition.control;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionStatus;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.competition.entity.LeaguePhase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeagueProgressNotifierTest {

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private LeagueProgressNotifier notifier;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID competitionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notifier = new LeagueProgressNotifier(sseEmitterRegistry);
    }

    @Test
    void notifyBreakOrPlayoffIfStarted_publishesMatchdayBreak() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.BREAK_AFTER_MD1);
        competition.setIntermissionEndsAt(Instant.now().plusSeconds(15));

        notifier.notifyBreakOrPlayoffIfStarted(sessionId, competition, true);

        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("league_matchday_break"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void notifyBreakOrPlayoffIfStarted_publishesPlayoffStarted() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.CHAMPIONSHIP_PLAYOFF);

        notifier.notifyBreakOrPlayoffIfStarted(sessionId, competition, true);

        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("league_playoff_started"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void notifyBreakOrPlayoffIfStarted_noOpWhenNotAdvanced() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.BREAK_AFTER_MD1);

        notifier.notifyBreakOrPlayoffIfStarted(sessionId, competition, false);

        verify(sseEmitterRegistry, never()).broadcast(eq(sessionId), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void notifyFinalState_completedPublishesChampion() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.REPLAYS);
        competition.setStatus(CompetitionStatus.COMPLETED);
        UUID championId = UUID.randomUUID();
        competition.setChampionUserId(championId);
        Runnable callback = org.mockito.Mockito.mock(Runnable.class);

        notifier.notifyFinalState(sessionId, competition, callback);

        verify(callback).run();
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("league_completed"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void notifyFinalState_inProgressPublishesUpdated() {
        CompetitionEntity competition = leagueCompetition(LeaguePhase.MATCHDAY_2);

        notifier.notifyFinalState(sessionId, competition, () -> {});

        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("league_updated"), org.mockito.ArgumentMatchers.any());
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
