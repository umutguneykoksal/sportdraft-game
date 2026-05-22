package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoundCardSelectorTest {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID user1 = UUID.randomUUID();
    private final UUID user2 = UUID.randomUUID();

    @Test
    void findNextEligibleRound_skipsSoldCards() {
        UUID soldId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();
        ActiveSession session = sessionWithPool(soldId, nextId);
        session.getRounds().add(soldRound(soldId));
        Map<UUID, Card> cardMap = Map.of(
            soldId, card(soldId, Position.FWD),
            nextId, card(nextId, Position.FWD)
        );

        RoundCardSelector.SelectionResult result = RoundCardSelector.findNextEligibleRound(session, cardMap);

        assertThat(result).isNotNull();
        assertThat(result.round().getCardId()).isEqualTo(nextId);
    }

    @Test
    void findNextEligibleRound_skipsCardsNoPlayerCanFill() {
        UUID gkOnlyId = UUID.randomUUID();
        ActiveSession session = sessionWithPool(gkOnlyId);
        UUID existingGk = UUID.randomUUID();
        session.getPlayers().forEach(p -> p.getCardIds().add(existingGk));
        Map<UUID, Card> cardMap = Map.of(
            gkOnlyId, card(gkOnlyId, Position.GK),
            existingGk, card(existingGk, Position.GK)
        );

        RoundCardSelector.SelectionResult result = RoundCardSelector.findNextEligibleRound(session, cardMap);

        assertThat(result).isNull();
    }

    @Test
    void findNextEligibleRound_rotatesOpeningBidderAfterSoldRound() {
        UUID card1 = UUID.randomUUID();
        UUID card2 = UUID.randomUUID();
        ActiveSession session = sessionWithPool(card1, card2);
        ActivePlayer winner = session.getPlayers().get(0);
        ActiveRound prev = new ActiveRound(
            UUID.randomUUID(), card1, Instant.now(), Instant.now(),
            user1, RoundStatus.SOLD
        );
        prev.setHighestBidderId(winner.getId());
        session.getRounds().add(prev);
        session.ensureTurnOrder();
        Map<UUID, Card> cardMap = Map.of(
            card1, card(card1, Position.FWD),
            card2, card(card2, Position.FWD)
        );

        RoundCardSelector.SelectionResult result = RoundCardSelector.findNextEligibleRound(session, cardMap);

        assertThat(result).isNotNull();
        assertThat(result.round().getOpeningBidderUserId()).isEqualTo(user2);
    }

    @Test
    void findNextEligibleRound_returnsNullWhenPoolExhausted() {
        UUID cardId = UUID.randomUUID();
        ActiveSession session = sessionWithPool(cardId);
        session.getRounds().add(soldRound(cardId));
        Map<UUID, Card> cardMap = Map.of(cardId, card(cardId, Position.FWD));

        RoundCardSelector.SelectionResult result = RoundCardSelector.findNextEligibleRound(session, cardMap);

        assertThat(result).isNull();
    }

    private ActiveSession sessionWithPool(UUID... cardIds) {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now(), GameMode.DUEL);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user1, "P1", Formation.FOUR_FOUR_TWO, 100));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user2, "P2", Formation.FOUR_FOUR_TWO, 100));
        session.setCardPool(List.of(cardIds));
        session.ensureTurnOrder();
        return session;
    }

    private static ActiveRound soldRound(UUID cardId) {
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), cardId, Instant.now(), Instant.now(),
            UUID.randomUUID(), RoundStatus.SOLD
        );
        round.markSold();
        return round;
    }

    private static Card card(UUID id, Position position) {
        return new Card(id, "Player", position, 80);
    }
}
