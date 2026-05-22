package com.example.auction.competition.control;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.common.events.ProcessedEventRepository;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.simulation.control.DraftCompletedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class DraftCompletedListenerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private CompetitionModeRegistry competitionModeRegistry;
    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private DraftCompletedListener listener;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID competitionId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new DraftCompletedListener(
            processedEventRepository,
            competitionModeRegistry,
            competitionRepository,
            sseEmitterRegistry
        );
    }

    @Test
    void onDraftCompleted_initializesCompetitionAndBroadcastsAfterCommit() {
        DraftCompletedEvent event = draftEvent(GameMode.TOURNAMENT);
        CompetitionEntity competition = new CompetitionEntity();
        competition.setId(competitionId);

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(competitionModeRegistry.supports(GameMode.TOURNAMENT)).thenReturn(true);
        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(competitionModeRegistry.initialize(event)).thenReturn(competition);
        when(competitionModeRegistry.startedEventType(GameMode.TOURNAMENT)).thenReturn("tournament_started");

        TransactionSynchronizationManager.initSynchronization();
        try {
            listener.onDraftCompleted(event);
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clear();
        }

        verify(processedEventRepository).save(any());
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("tournament_started"), any());
    }

    @Test
    void onDraftCompleted_skipsUnsupportedGameMode() {
        DraftCompletedEvent event = draftEvent(GameMode.DUEL);
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(competitionModeRegistry.supports(GameMode.DUEL)).thenReturn(false);

        listener.onDraftCompleted(event);

        verify(competitionRepository, never()).findBySessionId(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void onDraftCompleted_skipsDuplicateEvent() {
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        listener.onDraftCompleted(draftEvent(GameMode.LEAGUE));

        verify(competitionRepository, never()).findBySessionId(any());
    }

    private DraftCompletedEvent draftEvent(GameMode mode) {
        UUID playerId = UUID.randomUUID();
        return new DraftCompletedEvent(
            eventId,
            Instant.now(),
            sessionId,
            mode,
            10L,
            List.of(new DraftCompletedEvent.DraftPlayerRoster(
                playerId, "P1", Formation.FOUR_FOUR_TWO, List.of()
            ))
        );
    }
}
