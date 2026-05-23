package dev.sportdraft.simulation.control;

import dev.sportdraft.common.events.ProcessedEvent;
import dev.sportdraft.common.events.ProcessedEventRepository;
import dev.sportdraft.simulation.entity.SimulationResult;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MatchSimulationListener {

    private static final Logger log = LoggerFactory.getLogger(MatchSimulationListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final MatchSimulator matchSimulator;
    private final ApplicationEventPublisher publisher;

    public MatchSimulationListener(
        ProcessedEventRepository processedEventRepository,
        MatchSimulator matchSimulator,
        ApplicationEventPublisher publisher
    ) {
        this.processedEventRepository = processedEventRepository;
        this.matchSimulator = matchSimulator;
        this.publisher = publisher;
    }

    @EventListener
    @Async
    @Transactional
    public void onSessionFinalized(SessionFinalizedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        try {
            processedEventRepository.save(
                new ProcessedEvent(event.eventId(), "SessionFinalizedEvent", Instant.now())
            );
        } catch (DataIntegrityViolationException duplicate) {
            return;
        }

        log.info("Simulating duel session={} seed={}", event.sessionId(), event.seed());
        SimulationResult result = matchSimulator.simulateWithTimeline(
            event.seed(),
            event.homeFormation(),
            event.awayFormation(),
            event.homeRoster(),
            event.awayRoster(),
            event.homePlayerId(),
            event.awayPlayerId()
        );

        log.info(
            "Duel simulated session={} score={}-{} penalties={}-{}",
            event.sessionId(),
            result.homeGoals(),
            result.awayGoals(),
            result.homePenaltyGoals(),
            result.awayPenaltyGoals()
        );
        publisher.publishEvent(
            new MatchCompletedEvent(
                UUID.randomUUID(),
                Instant.now(),
                event.sessionId(),
                result.homeGoals(),
                result.awayGoals(),
                event.seed(),
                result.timeline(),
                result.homePenaltyGoals(),
                result.awayPenaltyGoals(),
                result.resolveWinner(event.homePlayerId(), event.awayPlayerId())
            )
        );
    }
}
