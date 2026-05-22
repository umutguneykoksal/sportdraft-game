package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveBid;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuctionBidValidatorTest {

    private final UUID user1 = UUID.randomUUID();
    private final UUID user2 = UUID.randomUUID();
    private final UUID cardId = UUID.randomUUID();

    @Test
    void notYourTurn_throwsConflict() {
        ActiveSession session = duelSession(user1, user2, 50, 50);
        Card card = card(Position.FWD, 70);
        ActiveRound round = openRound(user2);
        round.setTurnStartedAt(Instant.now());
        ActivePlayer bidder = session.findPlayerByUserId(user1).orElseThrow();

        assertThatThrownBy(() -> AuctionBidValidator.validateBid(
            session, round, bidder, card, new PlaceBidRequest(round.getId(), 1), Map.of(cardId, card), user1
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Not your turn to bid");
            });
    }

    @Test
    void belowMinimumIncrement_throws422() {
        ActiveSession session = duelSession(user1, user2, 50, 50);
        Card card = card(Position.FWD, 70);
        ActiveRound round = openRound(user1);
        ActivePlayer bidder = session.findPlayerByUserId(user1).orElseThrow();

        assertThatThrownBy(() -> AuctionBidValidator.validateBid(
            session, round, bidder, card, new PlaceBidRequest(round.getId(), 0), Map.of(cardId, card), user1
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(rse.getReason()).isEqualTo("Bid is below minimum increment");
            });
    }

    @Test
    void insufficientBudget_throws422() {
        ActiveSession session = duelSession(user1, user2, 5, 50);
        Card card = card(Position.FWD, 70);
        ActiveRound round = openRound(user1);
        ActivePlayer bidder = session.findPlayerByUserId(user1).orElseThrow();

        assertThatThrownBy(() -> AuctionBidValidator.validateBid(
            session, round, bidder, card, new PlaceBidRequest(round.getId(), 10), Map.of(cardId, card), user1
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(rse.getReason()).startsWith("Insufficient budget");
            });
    }

    @Test
    void alreadyLeadingBidder_throwsConflict() {
        ActiveSession session = duelSession(user1, user2, 50, 50);
        Card card = card(Position.FWD, 70);
        ActivePlayer leader = session.findPlayerByUserId(user1).orElseThrow();
        ActiveRound round = openRound(user1);
        round.setHighestBidderId(leader.getId());
        round.registerBid(leader.getId(), 2L, round.getEndsAt(), user2);
        round.setTurnStartedAt(Instant.now().minus(20, ChronoUnit.SECONDS));
        round.setCurrentTurnUserId(user1);
        round.getBids().add(new ActiveBid(UUID.randomUUID(), leader.getId(), 2, UUID.randomUUID(), Instant.now()));

        assertThatThrownBy(() -> AuctionBidValidator.validateBid(
            session, round, leader, card, new PlaceBidRequest(round.getId(), 3), Map.of(cardId, card), user1
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("You already hold the highest bid; wait for others");
            });
    }

    @Test
    void reserveRequiredForRemainingSlots_throws422() {
        ActiveSession session = duelSession(user1, user2, 3, 50);
        Card card = card(Position.FWD, 70);
        ActiveRound round = openRound(user1);
        ActivePlayer bidder = session.findPlayerByUserId(user1).orElseThrow();

        assertThatThrownBy(() -> AuctionBidValidator.validateBid(
            session, round, bidder, card, new PlaceBidRequest(round.getId(), 2), Map.of(cardId, card), user1
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(rse.getReason()).startsWith("Must reserve");
            });
    }

    @Test
    void rosterFull_throws422() {
        ActiveSession session = duelSession(user1, user2, 50, 50);
        Card card = card(Position.FWD, 70);
        ActiveRound round = openRound(user1);
        ActivePlayer bidder = session.findPlayerByUserId(user1).orElseThrow();
        for (int i = 0; i < 11; i++) {
            bidder.addCard(UUID.randomUUID(), Position.DEF);
        }

        assertThatThrownBy(() -> AuctionBidValidator.validateBid(
            session, round, bidder, card, new PlaceBidRequest(round.getId(), 1), Map.of(cardId, card), user1
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(rse.getReason()).isEqualTo("Roster is full");
            });
    }

    @Test
    void noOpenSlot_throws422() {
        ActiveSession session = duelSession(user1, user2, 50, 50);
        UUID fwdCardId = UUID.randomUUID();
        Card fwdCard = card(Position.FWD, 70);
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(),
            fwdCardId,
            Instant.now(),
            Instant.now().plus(30, ChronoUnit.SECONDS),
            user1,
            RoundStatus.OPEN
        );
        ActivePlayer bidder = session.findPlayerByUserId(user1).orElseThrow();
        bidder.addCard(UUID.randomUUID(), Position.DEF);
        bidder.addCard(UUID.randomUUID(), Position.DEF);
        bidder.addCard(UUID.randomUUID(), Position.DEF);
        bidder.addCard(UUID.randomUUID(), Position.DEF);
        bidder.addCard(UUID.randomUUID(), Position.CM);
        bidder.addCard(UUID.randomUUID(), Position.CM);
        bidder.addCard(UUID.randomUUID(), Position.CM);
        bidder.addCard(UUID.randomUUID(), Position.CM);
        bidder.addCard(UUID.randomUUID(), Position.FWD);
        bidder.addCard(UUID.randomUUID(), Position.FWD);

        assertThatThrownBy(() -> AuctionBidValidator.validateBid(
            session, round, bidder, fwdCard, new PlaceBidRequest(round.getId(), 1), Map.of(fwdCardId, fwdCard), user1
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(rse.getReason()).isEqualTo("No open slot for this card");
            });
    }

    private ActiveSession duelSession(UUID firstUser, UUID secondUser, long budget1, long budget2) {
        ActiveSession session = new ActiveSession(UUID.randomUUID(), SessionStatus.AUCTIONING, Instant.now(), GameMode.DUEL);
        session.setTurnOrder(List.of(firstUser, secondUser));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), firstUser, "P1", Formation.FOUR_FOUR_TWO, budget1));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), secondUser, "P2", Formation.FOUR_FOUR_TWO, budget2));
        return session;
    }

    private ActiveRound openRound(UUID turnUserId) {
        return new ActiveRound(
            UUID.randomUUID(),
            cardId,
            Instant.now(),
            Instant.now().plus(30, ChronoUnit.SECONDS),
            turnUserId,
            RoundStatus.OPEN
        );
    }

    private Card card(Position position, int rating) {
        return new Card(cardId, "Test Card", position, rating);
    }
}
