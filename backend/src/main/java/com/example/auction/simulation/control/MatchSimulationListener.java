package com.example.auction.simulation.control;

import com.example.auction.common.events.ProcessedEvent;
import com.example.auction.common.events.ProcessedEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MatchSimulationListener {

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

        MatchSimulator.Score score = matchSimulator.simulate(
            event.seed(),
            event.homeFormation(),
            event.awayFormation(),
            event.homeRoster(),
            event.awayRoster()
        );

        publisher.publishEvent(
            new MatchCompletedEvent(
                UUID.randomUUID(),
                Instant.now(),
                event.sessionId(),
                score.homeGoals(),
                score.awayGoals(),
                event.seed()
            )
        );
    }
}
