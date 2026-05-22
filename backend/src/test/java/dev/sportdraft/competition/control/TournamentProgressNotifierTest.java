package dev.sportdraft.competition.control;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionStatus;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.competition.entity.TournamentPhase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TournamentProgressNotifierTest {

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private TournamentProgressNotifier notifier;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID competitionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notifier = new TournamentProgressNotifier(sseEmitterRegistry);
    }

    @Test
    void notifyIntermissionStarted_broadcastsEvent() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.INTERMISSION);

        notifier.notifyIntermissionStarted(sessionId, competition);

        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("tournament_intermission"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void notifyFinalState_completedRunsCallbackAndBroadcasts() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.REPLAYS);
        competition.setStatus(CompetitionStatus.COMPLETED);
        competition.setChampionUserId(UUID.randomUUID());
        Runnable callback = org.mockito.Mockito.mock(Runnable.class);

        notifier.notifyFinalState(sessionId, competition, callback);

        verify(callback).run();
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("tournament_completed"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void notifyFinalState_inProgressBroadcastsUpdated() {
        CompetitionEntity competition = tournamentCompetition(TournamentPhase.FINAL);

        notifier.notifyFinalState(sessionId, competition, () -> {});

        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("tournament_updated"), org.mockito.ArgumentMatchers.any());
    }

    private CompetitionEntity tournamentCompetition(TournamentPhase phase) {
        CompetitionEntity entity = new CompetitionEntity();
        entity.setId(competitionId);
        entity.setSessionId(sessionId);
        entity.setType(CompetitionType.TOURNAMENT);
        entity.setStatus(CompetitionStatus.IN_PROGRESS);
        entity.setTournamentPhase(phase);
        entity.setIntermissionEndsAt(Instant.now().plusSeconds(15));
        return entity;
    }
}
