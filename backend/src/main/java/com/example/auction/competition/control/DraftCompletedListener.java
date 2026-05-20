package com.example.auction.competition.control;

import com.example.auction.common.events.ProcessedEvent;
import com.example.auction.common.events.ProcessedEventRepository;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.CompetitionType;
import com.example.auction.session.entity.GameMode;
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
    private final SseEmitterRegistry sseEmitterRegistry;

    public DraftCompletedListener(
        ProcessedEventRepository processedEventRepository,
        TournamentBracketService tournamentBracketService,
        CompetitionRepository competitionRepository,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.processedEventRepository = processedEventRepository;
        this.tournamentBracketService = tournamentBracketService;
        this.competitionRepository = competitionRepository;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @EventListener
    @Async
    @Transactional
    public void onDraftCompleted(DraftCompletedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }

        if (event.gameMode() == GameMode.TOURNAMENT) {
            CompetitionEntity competition = competitionRepository.findBySessionId(event.sessionId())
                .orElseGet(() -> tournamentBracketService.initializeTournament(event));
            sseEmitterRegistry.broadcast(
                event.sessionId(),
                "tournament_started",
                Map.of(
                    "sessionId", event.sessionId(),
                    "competitionId", competition.getId()
                )
            );
        } else if (event.gameMode() == GameMode.LEAGUE) {
            CompetitionEntity competition = competitionRepository.findBySessionId(event.sessionId())
                .orElseGet(() -> {
                    CompetitionEntity created = new CompetitionEntity();
                    created.setId(UUID.randomUUID());
                    created.setSessionId(event.sessionId());
                    created.setType(CompetitionType.LEAGUE);
                    created.setStatus(CompetitionStatus.PENDING);
                    created.setCreatedAt(Instant.now());
                    return competitionRepository.save(created);
                });
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

        try {
            processedEventRepository.save(
                new ProcessedEvent(event.eventId(), "DraftCompletedEvent", Instant.now())
            );
        } catch (DataIntegrityViolationException duplicate) {
            // Another worker handled this event.
        }
    }
}
