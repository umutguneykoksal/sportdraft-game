package com.example.auction.competition.control;

import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LeagueCompetitionRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(LeagueCompetitionRecoveryService.class);

    private final ActiveSessionStore sessionStore;
    private final CardCatalogService cardCatalog;
    private final CompetitionRepository competitionRepository;
    private final LeagueFixtureService leagueFixtureService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public LeagueCompetitionRecoveryService(
        ActiveSessionStore sessionStore,
        CardCatalogService cardCatalog,
        CompetitionRepository competitionRepository,
        LeagueFixtureService leagueFixtureService,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.sessionStore = sessionStore;
        this.cardCatalog = cardCatalog;
        this.competitionRepository = competitionRepository;
        this.leagueFixtureService = leagueFixtureService;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    public boolean recoverIfMissing(UUID sessionId) {
        if (competitionRepository.findBySessionId(sessionId).isPresent()) {
            return true;
        }

        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        if (!CompetitionRecoveryTemplate.isEligibleForRecovery(session, GameMode.LEAGUE)) {
            return false;
        }

        try {
            var event = TournamentCompetitionRecoveryService.buildDraftCompletedEvent(
                session,
                cardCatalog.getCardMap()
            );
            CompetitionEntity competition = leagueFixtureService.initializeLeague(event);
            sseEmitterRegistry.broadcast(
                sessionId,
                "league_started",
                Map.of(
                    "sessionId", sessionId,
                    "competitionId", competition.getId()
                )
            );
            log.info("Recovered missing league competition for session {}", sessionId);
            return true;
        } catch (Exception ex) {
            log.error("Failed to recover league competition for session {}", sessionId, ex);
            return false;
        }
    }
}
