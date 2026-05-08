package com.example.auction.simulation.control;

import com.example.auction.common.events.ProcessedEventRepository;
import com.example.auction.common.events.ProcessedEvent;
import com.example.auction.simulation.entity.MatchResult;
import com.example.auction.simulation.entity.MatchResultRepository;
import com.example.auction.session.entity.GameSession;
import com.example.auction.session.entity.GameSessionRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchSimulationListener {

    private final ProcessedEventRepository processedEventRepository;
    private final GameSessionRepository gameSessionRepository;
    private final MatchResultRepository matchResultRepository;
    private final MatchSimulator matchSimulator;
    private final ApplicationEventPublisher publisher;

    public MatchSimulationListener(
        ProcessedEventRepository processedEventRepository,
        GameSessionRepository gameSessionRepository,
        MatchResultRepository matchResultRepository,
        MatchSimulator matchSimulator,
        ApplicationEventPublisher publisher
    ) {
        this.processedEventRepository = processedEventRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.matchResultRepository = matchResultRepository;
        this.matchSimulator = matchSimulator;
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
            // Another concurrent consumer already marked this event.
            return;
        }

        GameSession session = gameSessionRepository.findById(event.sessionId())
            .orElseThrow(() -> new IllegalStateException("Session not found for simulation"));

        MatchSimulator.Score score = matchSimulator.simulate(
            event.seed(),
            event.homeFormation(),
            event.awayFormation(),
            event.homeRoster(),
            event.awayRoster()
        );

        MatchResult result = new MatchResult(
            UUID.randomUUID(),
            session,
            score.homeGoals(),
            score.awayGoals(),
            event.seed(),
            Instant.now()
        );
        matchResultRepository.save(result);

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
