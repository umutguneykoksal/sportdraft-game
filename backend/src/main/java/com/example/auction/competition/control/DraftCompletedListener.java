package com.example.auction.competition.control;

import com.example.auction.common.events.ProcessedEvent;
import com.example.auction.common.events.ProcessedEventRepository;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.CompetitionType;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.control.DraftCompletedEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DraftCompletedListener {

    private final ProcessedEventRepository processedEventRepository;
    private final TournamentBracketService tournamentBracketService;
    private final CompetitionRepository competitionRepository;
    private final ActiveSessionStore sessionStore;
    private final SseEmitterRegistry sseEmitterRegistry;

    public DraftCompletedListener(
        ProcessedEventRepository processedEventRepository,
        TournamentBracketService tournamentBracketService,
        CompetitionRepository competitionRepository,
        ActiveSessionStore sessionStore,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.processedEventRepository = processedEventRepository;
        this.tournamentBracketService = tournamentBracketService;
        this.competitionRepository = competitionRepository;
        this.sessionStore = sessionStore;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @EventListener
    @Async
    @Transactional
    public void onDraftCompleted(DraftCompletedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        try {
            processedEventRepository.save(
                new ProcessedEvent(event.eventId(), "DraftCompletedEvent", Instant.now())
            );
        } catch (DataIntegrityViolationException duplicate) {
            return;
        }

        if (event.gameMode() == GameMode.TOURNAMENT) {
            CompetitionEntity competition = tournamentBracketService.initializeTournament(event);
            finishSession(event.sessionId());
            sseEmitterRegistry.broadcast(
                event.sessionId(),
                "tournament_started",
                Map.of(
                    "sessionId", event.sessionId(),
                    "competitionId", competition.getId()
                )
            );
            return;
        }

        if (event.gameMode() == GameMode.LEAGUE) {
            CompetitionEntity competition = new CompetitionEntity();
            competition.setId(UUID.randomUUID());
            competition.setSessionId(event.sessionId());
            competition.setType(CompetitionType.LEAGUE);
            competition.setStatus(CompetitionStatus.PENDING);
            competition.setCreatedAt(Instant.now());
            competitionRepository.save(competition);
            sseEmitterRegistry.broadcast(
                event.sessionId(),
                "league_pending",
                Map.of(
                    "sessionId", event.sessionId(),
                    "competitionId", competition.getId(),
                    "message", "League mode coming in a future update"
                )
            );
        }
    }

    private void finishSession(UUID sessionId) {
        sessionStore.findById(sessionId).ifPresent(session -> {
            long vBefore = session.getVersion();
            session.moveToFinished();
            sessionStore.saveIfVersionMatch(session, vBefore);
        });
    }
}
