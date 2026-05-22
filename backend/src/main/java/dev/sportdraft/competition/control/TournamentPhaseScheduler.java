package dev.sportdraft.competition.control;

import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionStatus;
import dev.sportdraft.competition.entity.TournamentPhase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TournamentPhaseScheduler {

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchRepository matchRepository;
    private final TournamentBracketService bracketService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public TournamentPhaseScheduler(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        TournamentBracketService bracketService,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.bracketService = bracketService;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void advanceIntermissionToFinal() {
        List<CompetitionEntity> inProgress = competitionRepository.findByStatus(CompetitionStatus.IN_PROGRESS);
        for (CompetitionEntity competition : inProgress) {
            if (competition.getTournamentPhase() != TournamentPhase.INTERMISSION) {
                continue;
            }
            if (!bracketService.tryStartFinalPhase(competition)) {
                continue;
            }
            CompetitionMatchEntity finalMatch = matchRepository
                .findByCompetitionIdAndRoundKey(competition.getId(), "FINAL")
                .orElse(null);
            if (finalMatch == null) {
                continue;
            }
            UUID sessionId = competition.getSessionId();
            sseEmitterRegistry.broadcast(
                sessionId,
                "tournament_final_start",
                Map.of(
                    "sessionId", sessionId,
                    "competitionId", competition.getId(),
                    "matchId", finalMatch.getId(),
                    "tournamentPhase", TournamentPhase.FINAL.name()
                )
            );
            sseEmitterRegistry.broadcast(
                sessionId,
                "tournament_updated",
                Map.of("sessionId", sessionId, "competitionId", competition.getId())
            );
        }
    }
}
