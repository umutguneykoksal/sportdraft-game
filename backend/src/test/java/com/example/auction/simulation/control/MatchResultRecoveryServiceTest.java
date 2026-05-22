package com.example.auction.simulation.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.Position;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.entity.MatchEvent;
import com.example.auction.simulation.entity.MatchEventType;
import com.example.auction.simulation.entity.SimulationResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MatchResultRecoveryServiceTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private CardCatalogService cardCatalog;
    @Mock
    private MatchSimulator matchSimulator;
    @Mock
    private ApplicationEventPublisher publisher;

    private MatchResultRecoveryService recoveryService;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID homeUserId = UUID.randomUUID();
    private final UUID awayUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        recoveryService = new MatchResultRecoveryService(
            sessionStore,
            cardCatalog,
            matchSimulator,
            publisher
        );
    }

    @Test
    void recoverIfMissing_noSession_returnsFalse() {
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());

        assertThat(recoveryService.recoverIfMissing(sessionId)).isFalse();
    }

    @Test
    void recoverIfMissing_scoreAlreadyPresent_returnsTrue() {
        ActiveSession session = duelSession(SessionStatus.FINISHED);
        session.setHomeScore(2);
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));

        assertThat(recoveryService.recoverIfMissing(sessionId)).isTrue();
        verify(matchSimulator, never()).simulateWithTimeline(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void recoverIfMissing_nonDuel_returnsFalse() {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.FINALIZING, Instant.now(), GameMode.TOURNAMENT);
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));

        assertThat(recoveryService.recoverIfMissing(sessionId)).isFalse();
    }

    @Test
    void recoverIfMissing_wrongStatus_returnsFalse() {
        ActiveSession session = duelSession(SessionStatus.AUCTIONING);
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));

        assertThat(recoveryService.recoverIfMissing(sessionId)).isFalse();
    }

    @Test
    void recoverIfMissing_incompleteRoster_returnsFalse() {
        ActiveSession session = duelSession(SessionStatus.FINALIZING);
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));

        assertThat(recoveryService.recoverIfMissing(sessionId)).isFalse();
    }

    @Test
    void recoverIfMissing_finalizingWithFullRosters_simulatesAndPublishes() {
        ActiveSession session = duelSession(SessionStatus.FINALIZING);
        Map<UUID, Card> cardMap = fillBothRosters(session);
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));
        when(cardCatalog.getCardMap()).thenReturn(cardMap);
        when(matchSimulator.simulateWithTimeline(anyLong(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new SimulationResult(2, 1, List.of(
                MatchEvent.forPlayer(10, MatchEventType.GOAL, homeUserId, UUID.randomUUID(), "Striker", "Goal")
            )));

        assertThat(recoveryService.recoverIfMissing(sessionId)).isTrue();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session.getHomeScore()).isEqualTo(2);
        assertThat(session.getAwayScore()).isEqualTo(1);
        verify(sessionStore).saveIfVersionMatch(eq(session), eq(session.getVersion()));
        ArgumentCaptor<MatchCompletedEvent> eventCaptor = ArgumentCaptor.forClass(MatchCompletedEvent.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().homeScore()).isEqualTo(2);
    }

    private ActiveSession duelSession(SessionStatus status) {
        ActiveSession session = new ActiveSession(sessionId, status, Instant.now(), GameMode.DUEL);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), homeUserId, "Home", Formation.FOUR_FOUR_TWO, 0));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), awayUserId, "Away", Formation.FOUR_FOUR_TWO, 0));
        return session;
    }

    private Map<UUID, Card> fillBothRosters(ActiveSession session) {
        Map<UUID, Card> cardMap = new HashMap<>();
        for (ActivePlayer player : session.getPlayers()) {
            List<UUID> cardIds = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                UUID cardId = UUID.randomUUID();
                cardIds.add(cardId);
                cardMap.put(cardId, new Card(cardId, "P" + i, Position.FWD, 80));
            }
            player.setCardIds(cardIds);
        }
        return cardMap;
    }
}
