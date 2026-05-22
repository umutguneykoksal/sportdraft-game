package dev.sportdraft.competition.control;

import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionStatus;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.competition.entity.LeaguePhase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LeaguePhaseScheduler {

    private final CompetitionRepository competitionRepository;
    private final LeagueFixtureService leagueService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public LeaguePhaseScheduler(
        CompetitionRepository competitionRepository,
        LeagueFixtureService leagueService,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.competitionRepository = competitionRepository;
        this.leagueService = leagueService;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void advanceMatchdayBreaks() {
        List<CompetitionEntity> inProgress = competitionRepository.findByStatus(CompetitionStatus.IN_PROGRESS);
        for (CompetitionEntity competition : inProgress) {
            if (competition.getType() != CompetitionType.LEAGUE) {
                continue;
            }
            if (!competition.getLeaguePhase().isMatchdayBreak()) {
                continue;
            }
            if (!leagueService.tryAdvanceFromBreak(competition)) {
                continue;
            }
            UUID sessionId = competition.getSessionId();
            sseEmitterRegistry.broadcast(
                sessionId,
                "league_matchday_started",
                Map.of(
                    "sessionId", sessionId,
                    "competitionId", competition.getId(),
                    "leaguePhase", competition.getLeaguePhase().name()
                )
            );
            sseEmitterRegistry.broadcast(
                sessionId,
                "league_updated",
                Map.of("sessionId", sessionId, "competitionId", competition.getId())
            );
        }
    }
}
