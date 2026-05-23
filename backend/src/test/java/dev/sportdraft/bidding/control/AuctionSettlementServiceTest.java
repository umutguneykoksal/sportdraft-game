package dev.sportdraft.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.bidding.entity.RoundStatus;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionSettlementServiceTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private AuctionMatchTransition matchTransition;
    @Mock
    private AuctionRoundLifecycle roundLifecycle;

    private AuctionSettlementService settlement;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID user1 = UUID.randomUUID();
    private final UUID user2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        settlement = new AuctionSettlementService(
            sessionStore,
            sseEmitterRegistry,
            matchTransition,
            roundLifecycle
        );
    }

    @Test
    void closeRound_noBidder_marksPassedAndStartsNextRound() {
        ActiveSession session = auctioningSession();
        ActiveRound round = openRound(UUID.randomUUID(), session);
        Map<UUID, Card> cardMap = Map.of(round.getCardId(), card(round.getCardId(), Position.FWD));

        settlement.closeRound(round, session, session.getVersion(), cardMap);

        assertThat(round.getStatus()).isEqualTo(RoundStatus.PASSED);
        verify(sessionStore).saveIfVersionMatch(session, session.getVersion());
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("round_ended"), any());
        verify(roundLifecycle).maybeStartNextRound(session);
    }

    @Test
    void closeRound_winnerRosterFull_marksPassedWithReason() {
        ActiveSession session = auctioningSession();
        ActivePlayer winner = session.getPlayers().get(0);
        fillRoster(winner, Position.FWD);
        UUID cardId = UUID.randomUUID();
        ActiveRound round = openRound(cardId, session);
        round.setHighestBidderId(winner.getId());
        round.setHighestBidCents(5);
        Map<UUID, Card> cardMap = Map.of(cardId, card(cardId, Position.FWD));

        settlement.closeRound(round, session, session.getVersion(), cardMap);

        assertThat(round.getStatus()).isEqualTo(RoundStatus.PASSED);
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("round_ended"), any());
        verify(roundLifecycle).maybeStartNextRound(session);
    }

    @Test
    void closeRound_winnerPositionFull_marksPassedWithReason() {
        ActiveSession session = auctioningSession();
        ActivePlayer winner = session.getPlayers().get(0);
        UUID gkCardId = UUID.randomUUID();
        winner.getCardIds().add(gkCardId);
        UUID newGkCardId = UUID.randomUUID();
        ActiveRound round = openRound(newGkCardId, session);
        round.setHighestBidderId(winner.getId());
        round.setHighestBidCents(3);
        Map<UUID, Card> cardMap = Map.of(
            gkCardId, card(gkCardId, Position.GK),
            newGkCardId, card(newGkCardId, Position.GK)
        );

        settlement.closeRound(round, session, session.getVersion(), cardMap);

        assertThat(round.getStatus()).isEqualTo(RoundStatus.PASSED);
        verify(roundLifecycle).maybeStartNextRound(session);
    }

    @Test
    void closeRound_validWinner_marksSold() {
        ActiveSession session = auctioningSession();
        ActivePlayer winner = session.getPlayers().get(0);
        UUID cardId = UUID.randomUUID();
        ActiveRound round = openRound(cardId, session);
        round.setHighestBidderId(winner.getId());
        round.setHighestBidCents(10);
        Map<UUID, Card> cardMap = Map.of(cardId, card(cardId, Position.FWD));

        settlement.closeRound(round, session, session.getVersion(), cardMap);

        assertThat(round.getStatus()).isEqualTo(RoundStatus.SOLD);
        assertThat(winner.getCardIds()).contains(cardId);
        assertThat(winner.getRemainingBudgetCents()).isEqualTo(90);
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("round_ended"), any());
    }

    @Test
    void closeRound_winnerNotFound_throwsConflict() {
        ActiveSession session = auctioningSession();
        UUID cardId = UUID.randomUUID();
        ActiveRound round = openRound(cardId, session);
        round.setHighestBidderId(UUID.randomUUID());
        Map<UUID, Card> cardMap = Map.of(cardId, card(cardId, Position.FWD));

        assertThatThrownBy(() -> settlement.closeRound(round, session, session.getVersion(), cardMap))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
    }

    @Test
    void closeRound_fullRosters_entersMatchStartPending() {
        ActiveSession session = auctioningSession();
        fillRoster(session.getPlayers().get(0), Position.FWD);
        fillRoster(session.getPlayers().get(1), Position.FWD);
        UUID cardId = UUID.randomUUID();
        ActiveRound round = openRound(cardId, session);
        Map<UUID, Card> cardMap = Map.of(cardId, card(cardId, Position.FWD));

        settlement.closeRound(round, session, session.getVersion(), cardMap);

        verify(matchTransition).enterMatchStartPending(session, session.getVersion());
    }

    @Test
    void closeRound_skipsWhenRoundNotOpen() {
        ActiveSession session = auctioningSession();
        ActiveRound round = openRound(UUID.randomUUID(), session);
        round.setStatus(RoundStatus.SOLD);

        settlement.closeRound(round, session, session.getVersion(), Map.of());

        verify(sessionStore, never()).saveIfVersionMatch(any(), anyLong());
        verify(sseEmitterRegistry, never()).broadcast(any(), any(), any());
    }

    @Test
    void settleRoundInline_noWinner_marksPassed() {
        ActiveSession session = auctioningSession();
        UUID cardId = UUID.randomUUID();
        ActiveRound round = openRound(cardId, session);
        Map<UUID, Card> cardMap = Map.of(cardId, card(cardId, Position.FWD));

        settlement.settleRoundInline(round, session, cardMap);

        assertThat(round.getStatus()).isEqualTo(RoundStatus.PASSED);
    }

    @Test
    void settleRoundInline_validWinner_marksSold() {
        ActiveSession session = auctioningSession();
        ActivePlayer winner = session.getPlayers().get(0);
        UUID cardId = UUID.randomUUID();
        ActiveRound round = openRound(cardId, session);
        round.setHighestBidderId(winner.getId());
        round.setHighestBidCents(4);
        Map<UUID, Card> cardMap = Map.of(cardId, card(cardId, Position.FWD));

        settlement.settleRoundInline(round, session, cardMap);

        assertThat(round.getStatus()).isEqualTo(RoundStatus.SOLD);
    }

    @Test
    void broadcastSettledRound_openRound_skipsBroadcast() {
        ActiveSession session = auctioningSession();
        ActiveRound round = openRound(UUID.randomUUID(), session);

        settlement.broadcastSettledRound(round, session, Map.of());

        verify(sseEmitterRegistry, never()).broadcast(any(), any(), any());
    }

    @Test
    void broadcastSettledRound_passedWithBidder_usesWinnerCannotTakeReason() {
        ActiveSession session = auctioningSession();
        ActivePlayer winner = session.getPlayers().get(0);
        UUID cardId = UUID.randomUUID();
        ActiveRound round = openRound(cardId, session);
        round.markPassed();
        round.setHighestBidderId(winner.getId());
        Map<UUID, Card> cardMap = Map.of(cardId, card(cardId, Position.FWD));

        settlement.broadcastSettledRound(round, session, cardMap);

        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("round_ended"), any());
    }

    @Test
    void finalizeSettledRound_chainsNextRoundWhenRequested() {
        ActiveSession session = auctioningSession();
        UUID cardId = UUID.randomUUID();
        ActiveRound round = openRound(cardId, session);
        round.markPassed();
        Map<UUID, Card> cardMap = Map.of(cardId, card(cardId, Position.FWD));

        settlement.finalizeSettledRound(round, session, session.getVersion(), cardMap, true);

        verify(roundLifecycle).maybeStartNextRound(session);
    }

    private ActiveSession auctioningSession() {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now(), GameMode.DUEL);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user1, "P1", Formation.FOUR_FOUR_TWO, 100));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user2, "P2", Formation.FOUR_FOUR_TWO, 100));
        session.ensureTurnOrder();
        return session;
    }

    private ActiveRound openRound(UUID cardId, ActiveSession session) {
        Instant now = Instant.now();
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), cardId, now, now.plusSeconds(30),
            session.getTurnOrder().getFirst(), RoundStatus.OPEN
        );
        session.getRounds().add(round);
        return round;
    }

    private static Card card(UUID id, Position position) {
        return new Card(id, "Player", position, 80);
    }

    private static void fillRoster(ActivePlayer player, Position position) {
        List<UUID> cardIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            cardIds.add(UUID.randomUUID());
        }
        player.setCardIds(cardIds);
    }
}
