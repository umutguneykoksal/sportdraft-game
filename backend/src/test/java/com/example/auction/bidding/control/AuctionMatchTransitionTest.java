package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionMatchTransitionTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private CardCatalogService cardCatalog;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private ApplicationEventPublisher publisher;

    private AuctionMatchTransition matchTransition;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID user1 = UUID.randomUUID();
    private final UUID user2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        matchTransition = new AuctionMatchTransition(
            sessionStore,
            cardCatalog,
            sseEmitterRegistry,
            publisher
        );
    }

    @Test
    void confirmMatchStart_wrongPhase_throwsConflict() {
        ActiveSession session = duelSession(SessionStatus.AUCTIONING);

        assertThatThrownBy(() -> matchTransition.confirmMatchStart(session, user1))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Match start is not pending");
            });
    }

    @Test
    void confirmMatchStart_firstPlayerBroadcastsReady() {
        ActiveSession session = duelSession(SessionStatus.MATCH_START_PENDING);

        matchTransition.confirmMatchStart(session, user1);

        assertThat(session.isMatchStartReady(user1)).isTrue();
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("match_start_ready"), org.mockito.ArgumentMatchers.any());
        verify(publisher, org.mockito.Mockito.never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enterMatchStartPending_closesOpenRoundAndMovesToPending() {
        ActiveSession session = duelSession(SessionStatus.AUCTIONING);
        Map<UUID, Card> cardMap = fillRoster(session.getPlayers().get(0));
        cardMap.putAll(fillRoster(session.getPlayers().get(1)));
        when(cardCatalog.getCardMap()).thenReturn(cardMap);

        UUID cardId = cardMap.keySet().iterator().next();
        ActiveRound openRound = new ActiveRound(
            UUID.randomUUID(),
            cardId,
            Instant.now(),
            Instant.now().plus(30, ChronoUnit.SECONDS),
            user1,
            RoundStatus.OPEN
        );
        session.getRounds().add(openRound);

        matchTransition.enterMatchStartPending(session, session.getVersion());

        assertThat(session.getStatus()).isEqualTo(SessionStatus.MATCH_START_PENDING);
        assertThat(openRound.getStatus()).isEqualTo(RoundStatus.PASSED);
        verify(sessionStore).saveIfVersionMatch(eq(session), anyLong());
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("match_start_pending"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void finalizeSession_tournamentPublishesDraftCompleted() {
        ActiveSession session = tournamentSession(SessionStatus.MATCH_START_PENDING);
        Map<UUID, Card> cardMap = new HashMap<>();
        for (ActivePlayer player : session.getPlayers()) {
            cardMap.putAll(fillRoster(player));
        }
        when(cardCatalog.getCardMap()).thenReturn(cardMap);
        for (ActivePlayer player : session.getPlayers()) {
            session.registerMatchStartReady(player.getUserId());
        }

        matchTransition.confirmMatchStart(session, user1);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.AWAITING_RESULT);
        verify(publisher).publishEvent(isA(com.example.auction.simulation.control.DraftCompletedEvent.class));
    }

    @Test
    void confirmMatchStart_secondPlayerFinalizes() {
        ActiveSession session = duelSession(SessionStatus.MATCH_START_PENDING);
        Map<UUID, Card> cardMap = fillRoster(session.getPlayers().get(0));
        cardMap.putAll(fillRoster(session.getPlayers().get(1)));
        when(cardCatalog.getCardMap()).thenReturn(cardMap);

        matchTransition.confirmMatchStart(session, user1);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.MATCH_START_PENDING);
        assertThat(session.isMatchStartReady(user1)).isTrue();
        assertThat(session.isMatchStartReady(user2)).isFalse();

        matchTransition.confirmMatchStart(session, user2);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.AWAITING_RESULT);
        verify(publisher).publishEvent(isA(Object.class));
    }

    private ActiveSession tournamentSession(SessionStatus status) {
        ActiveSession session = new ActiveSession(sessionId, status, Instant.now(), GameMode.TOURNAMENT);
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();
        session.setTurnOrder(List.of(user1, user2, u3, u4));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user1, "P1", Formation.FOUR_FOUR_TWO, 0));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user2, "P2", Formation.FOUR_FOUR_TWO, 0));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), u3, "P3", Formation.FOUR_FOUR_TWO, 0));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), u4, "P4", Formation.FOUR_FOUR_TWO, 0));
        return session;
    }

    private ActiveSession duelSession(SessionStatus status) {
        ActiveSession session = new ActiveSession(sessionId, status, Instant.now(), GameMode.DUEL);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user1, "P1", Formation.FOUR_FOUR_TWO, 0));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user2, "P2", Formation.FOUR_FOUR_TWO, 0));
        return session;
    }

    private Map<UUID, Card> fillRoster(ActivePlayer player) {
        Map<UUID, Card> cardMap = new HashMap<>();
        List<UUID> cardIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            UUID cardId = UUID.randomUUID();
            cardIds.add(cardId);
            cardMap.put(cardId, new Card(cardId, "Player-" + i, Position.FWD, 80));
        }
        player.setCardIds(cardIds);
        return cardMap;
    }
}
