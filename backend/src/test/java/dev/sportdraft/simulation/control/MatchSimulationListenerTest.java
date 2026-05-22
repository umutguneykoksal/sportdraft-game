package dev.sportdraft.simulation.control;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.common.events.ProcessedEventRepository;
import dev.sportdraft.bidding.entity.PlayerAttributes;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.session.entity.Formation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MatchSimulationListenerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private ApplicationEventPublisher publisher;

    private MatchSimulationListener listener;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID homeId = UUID.randomUUID();
    private final UUID awayId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new MatchSimulationListener(
            processedEventRepository,
            new MatchSimulator(),
            publisher
        );
    }

    @Test
    void onSessionFinalized_publishesMatchCompleted() {
        SessionFinalizedEvent event = finalizedEvent();

        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);

        listener.onSessionFinalized(event);

        verify(processedEventRepository).save(any());
        verify(publisher).publishEvent(isA(MatchCompletedEvent.class));
    }

    @Test
    void onSessionFinalized_skipsDuplicateEvent() {
        SessionFinalizedEvent event = finalizedEvent();
        when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

        listener.onSessionFinalized(event);

        verify(processedEventRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    private SessionFinalizedEvent finalizedEvent() {
        List<SessionFinalizedEvent.RatedCard> roster = List.of(
            new SessionFinalizedEvent.RatedCard(
                UUID.randomUUID(), Position.FWD, 80, "Striker",
                new PlayerAttributes(80, 80, 80, 80, 80)
            ),
            new SessionFinalizedEvent.RatedCard(
                UUID.randomUUID(), Position.GK, 78, "Keeper",
                new PlayerAttributes(78, 78, 78, 78, 78)
            )
        );
        return new SessionFinalizedEvent(
            UUID.randomUUID(),
            Instant.now(),
            sessionId,
            99L,
            Formation.FOUR_FOUR_TWO,
            Formation.FOUR_THREE_THREE,
            roster,
            roster,
            homeId,
            awayId
        );
    }
}
