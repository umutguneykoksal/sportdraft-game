package dev.sportdraft.competition.control;

import dev.sportdraft.common.events.ProcessedEvent;
import dev.sportdraft.common.events.ProcessedEventRepository;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.simulation.control.DraftCompletedEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class DraftCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(DraftCompletedListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final CompetitionModeRegistry competitionModeRegistry;
    private final CompetitionRepository competitionRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    public DraftCompletedListener(
        ProcessedEventRepository processedEventRepository,
        CompetitionModeRegistry competitionModeRegistry,
        CompetitionRepository competitionRepository,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.processedEventRepository = processedEventRepository;
        this.competitionModeRegistry = competitionModeRegistry;
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

        try {
            if (!competitionModeRegistry.supports(event.gameMode())) {
                return;
            }

            CompetitionEntity competition = competitionRepository.findBySessionId(event.sessionId())
                .orElseGet(() -> competitionModeRegistry.initialize(event));
            log.info(
                "Competition started session={} competition={} mode={}",
                event.sessionId(), competition.getId(), event.gameMode()
            );
            broadcastAfterCommit(
                event.sessionId(),
                competitionModeRegistry.startedEventType(event.gameMode()),
                Map.of(
                    "sessionId", event.sessionId(),
                    "competitionId", competition.getId()
                )
            );

            processedEventRepository.save(
                new ProcessedEvent(event.eventId(), "DraftCompletedEvent", Instant.now())
            );
        } catch (Exception ex) {
            log.error(
                "Failed to initialize competition for session {} (gameMode={})",
                event.sessionId(),
                event.gameMode(),
                ex
            );
            throw ex;
        }
    }

    private void broadcastAfterCommit(UUID sessionId, String eventType, Map<String, Object> payload) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseEmitterRegistry.broadcast(sessionId, eventType, payload);
            }
        });
    }
}
