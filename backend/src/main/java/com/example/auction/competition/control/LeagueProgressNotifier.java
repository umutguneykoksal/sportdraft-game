package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.LeaguePhase;
import com.example.auction.common.sse.SseEmitterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class LeagueProgressNotifier {

    private final SseEmitterRegistry sseEmitterRegistry;

    LeagueProgressNotifier(SseEmitterRegistry sseEmitterRegistry) {
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    void notifyBreakOrPlayoffIfStarted(UUID sessionId, CompetitionEntity competition, boolean phaseAdvanced) {
        if (!phaseAdvanced) {
            return;
        }
        LeaguePhase phase = competition.getLeaguePhase();
        if (phase.isMatchdayBreak()) {
            publishMatchdayBreak(sessionId, competition);
        } else if (phase == LeaguePhase.CHAMPIONSHIP_PLAYOFF) {
            publishPlayoffStarted(sessionId, competition);
        }
    }

    void notifyFinalState(UUID sessionId, CompetitionEntity competition, Runnable onCompleted) {
        if (competition.getStatus() == CompetitionStatus.COMPLETED) {
            onCompleted.run();
            publishCompleted(sessionId, competition);
        } else {
            publishUpdated(sessionId, competition);
        }
    }

    private void publishMatchdayBreak(UUID sessionId, CompetitionEntity competition) {
        sseEmitterRegistry.broadcast(
            sessionId,
            "league_matchday_break",
            Map.of(
                "sessionId", sessionId,
                "competitionId", competition.getId(),
                "leaguePhase", competition.getLeaguePhase().name(),
                "intermissionEndsAt", competition.getIntermissionEndsAt()
            )
        );
    }

    private void publishPlayoffStarted(UUID sessionId, CompetitionEntity competition) {
        sseEmitterRegistry.broadcast(
            sessionId,
            "league_playoff_started",
            Map.of(
                "sessionId", sessionId,
                "competitionId", competition.getId(),
                "leaguePhase", competition.getLeaguePhase().name()
            )
        );
    }

    private void publishUpdated(UUID sessionId, CompetitionEntity competition) {
        sseEmitterRegistry.broadcast(
            sessionId,
            "league_updated",
            Map.of(
                "sessionId", sessionId,
                "competitionId", competition.getId(),
                "leaguePhase", competition.getLeaguePhase().name()
            )
        );
    }

    private void publishCompleted(UUID sessionId, CompetitionEntity competition) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("competitionId", competition.getId());
        payload.put("championUserId", competition.getChampionUserId());
        payload.put("leaguePhase", competition.getLeaguePhase().name());
        sseEmitterRegistry.broadcast(sessionId, "league_completed", payload);
    }
}
