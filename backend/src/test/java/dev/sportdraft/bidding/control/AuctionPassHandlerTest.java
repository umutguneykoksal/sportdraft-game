package dev.sportdraft.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.bidding.entity.RoundStatus;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveBid;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionPassHandlerTest {

    @Mock
    private ActiveSessionStore sessionStore;

    @Mock
    private AuctionSettlementService settlement;

    private AuctionPassHandler handler;

    private final UUID user1 = UUID.randomUUID();
    private final UUID user2 = UUID.randomUUID();
    private final UUID cardId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new AuctionPassHandler(sessionStore, settlement);
    }

    @Test
    void highestBidderPass_advancesTurnWhenAnotherBidderExists() {
        ActiveSession session = duelSession(user1, user2, 50, 50);
        Card card = card(Position.FWD, 70);
        ActivePlayer bidder = session.findPlayerByUserId(user1).orElseThrow();
        ActiveRound round = roundWithBidder(bidder.getId(), cardId, user2);

        AuctionPassHandler.PassRoundResult result = handler.pass(session, 1L, user1, round, Map.of(cardId, card));

        assertThat(result.broadcastTurnPassed()).isTrue();
        assertThat(round.getCurrentTurnUserId()).isEqualTo(user2);
        verify(sessionStore).saveIfVersionMatch(session, 1L);
    }

    @Test
    void highestBidderPass_settlesWhenNoOtherBidder() {
        ActiveSession session = duelSession(user1, user2, 0, 0);
        Card card = card(Position.FWD, 70);
        ActivePlayer bidder = session.findPlayerByUserId(user1).orElseThrow();
        ActiveRound round = roundWithBidder(bidder.getId(), cardId, user1);

        AuctionPassHandler.PassRoundResult result = handler.pass(session, 1L, user1, round, Map.of(cardId, card));

        assertThat(result.broadcastTurnPassed()).isFalse();
        verify(settlement).closeRound(eq(round), eq(session), eq(1L), any());
    }

    @Test
    void nonLeaderPass_settlesLeadingRoundInDuel() {
        ActiveSession session = duelSession(user1, user2, 50, 50);
        Card card = card(Position.FWD, 70);
        ActivePlayer leader = session.findPlayerByUserId(user2).orElseThrow();
        ActiveRound round = roundWithBidder(leader.getId(), cardId, user1);

        AuctionPassHandler.PassRoundResult result = handler.pass(session, 1L, user1, round, Map.of(cardId, card));

        assertThat(result.broadcastTurnPassed()).isFalse();
        verify(settlement).closeRound(eq(round), eq(session), eq(1L), any());
    }

    private ActiveSession duelSession(UUID firstUser, UUID secondUser, long budget1, long budget2) {
        ActiveSession session = new ActiveSession(UUID.randomUUID(), SessionStatus.AUCTIONING, Instant.now(), GameMode.DUEL);
        session.setTurnOrder(List.of(firstUser, secondUser));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), firstUser, "P1", Formation.FOUR_FOUR_TWO, budget1));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), secondUser, "P2", Formation.FOUR_FOUR_TWO, budget2));
        return session;
    }

    private ActiveRound roundWithBidder(UUID bidderPlayerId, UUID card, UUID turnUserId) {
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(),
            card,
            Instant.now(),
            Instant.now().plus(30, ChronoUnit.SECONDS),
            turnUserId,
            RoundStatus.OPEN
        );
        round.setHighestBidderId(bidderPlayerId);
        round.getBids().add(new ActiveBid(
            UUID.randomUUID(), bidderPlayerId, 2, UUID.randomUUID(), Instant.now()
        ));
        return round;
    }

    private Card card(Position position, int rating) {
        return new Card(cardId, "Test Card", position, rating);
    }
}
