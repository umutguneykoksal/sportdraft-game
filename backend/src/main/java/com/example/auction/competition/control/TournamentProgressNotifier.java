package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.TournamentPhase;
import com.example.auction.common.sse.SseEmitterRegistry;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TournamentProgressNotifier {

    private final SseEmitterRegistry sseEmitterRegistry;

    TournamentProgressNotifier(SseEmitterRegistry sseEmitterRegistry) {
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    void notifyIntermissionStarted(UUID sessionId, CompetitionEntity competition) {
        sseEmitterRegistry.broadcast(
            sessionId,
            "tournament_intermission",
            Map.of(
                "sessionId", sessionId,
                "competitionId", competition.getId(),
                "intermissionEndsAt", competition.getIntermissionEndsAt(),
                "tournamentPhase", TournamentPhase.INTERMISSION.name()
            )
        );
    }

    void notifyFinalState(UUID sessionId, CompetitionEntity competition, Runnable onCompleted) {
        if (competition.getStatus() == CompetitionStatus.COMPLETED) {
            onCompleted.run();
            sseEmitterRegistry.broadcast(
                sessionId,
                "tournament_completed",
                Map.of(
                    "sessionId", sessionId,
                    "competitionId", competition.getId(),
                    "championUserId", competition.getChampionUserId(),
                    "tournamentPhase", TournamentPhase.REPLAYS.name()
                )
            );
        } else {
            sseEmitterRegistry.broadcast(
                sessionId,
                "tournament_updated",
                Map.of(
                    "sessionId", sessionId,
                    "competitionId", competition.getId(),
                    "tournamentPhase", competition.getTournamentPhase().name()
                )
            );
        }
    }
}
