package dev.sportdraft.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.bidding.entity.RoundStatus;
import dev.sportdraft.session.redis.ActiveBid;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.session.redis.ActiveSession;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuctionRulesTest {

    @Test
    void nextTurnUserIdRotatesRoundRobin() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();
        ActiveSession session = fourPlayerSession(List.of(u1, u2, u3, u4));

        assertThat(AuctionRules.nextTurnUserId(session, u1)).isEqualTo(u2);
        assertThat(AuctionRules.nextTurnUserId(session, u2)).isEqualTo(u3);
        assertThat(AuctionRules.nextTurnUserId(session, u3)).isEqualTo(u4);
        assertThat(AuctionRules.nextTurnUserId(session, u4)).isEqualTo(u1);
    }

    @Test
    void isBiddingClosedDuringCardReveal() {
        Instant startsAt = Instant.now().minusMillis(500);
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), UUID.randomUUID(), startsAt,
            startsAt.plus(AuctionTiming.ROUND_DURATION_SECONDS, ChronoUnit.SECONDS),
            UUID.randomUUID(), RoundStatus.OPEN
        );

        assertThat(AuctionRules.isBiddingOpen(round)).isFalse();
        assertThat(AuctionRules.isTurnOpen(round)).isFalse();
    }

    @Test
    void isBiddingOpenAfterRevealWindow() {
        Instant startsAt = Instant.now().minus(AuctionTiming.ROUND_REVEAL_SECONDS + 1, ChronoUnit.SECONDS);
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), UUID.randomUUID(), startsAt,
            startsAt.plus(AuctionTiming.ROUND_DURATION_SECONDS, ChronoUnit.SECONDS),
            UUID.randomUUID(), RoundStatus.OPEN
        );

        assertThat(AuctionRules.isBiddingOpen(round)).isTrue();
    }

    @Test
    void turnOpensEarlyWhenRoundHasAtMostFifteenSecondsLeft() {
        Instant now = Instant.now();
        Instant startsAt = now.minus(AuctionTiming.ROUND_REVEAL_SECONDS + 1, ChronoUnit.SECONDS);
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), UUID.randomUUID(), startsAt, now.plus(10, ChronoUnit.SECONDS),
            UUID.randomUUID(), RoundStatus.OPEN
        );
        // Recent bid would normally open turn in 15s; round ends in 10s → open now.
        round.registerBid(UUID.randomUUID(), 1L, now.plus(10, ChronoUnit.SECONDS), UUID.randomUUID());

        assertThat(AuctionRules.computeTurnOpenAt(round)).isBeforeOrEqualTo(now.plus(1, ChronoUnit.SECONDS));
        assertThat(AuctionRules.isTurnOpen(round)).isTrue();
    }

    @Test
    void turnOpenAtIsEarlierOfExclusiveWindowAndFinalFifteenSeconds() {
        Instant now = Instant.now();
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), UUID.randomUUID(), now, now.plus(30, ChronoUnit.SECONDS),
            UUID.randomUUID(), RoundStatus.OPEN
        );
        round.setTurnStartedAt(now);
        round.setCurrentTurnUserId(UUID.randomUUID());

        Instant openAt = AuctionRules.computeTurnOpenAt(round);
        assertThat(openAt).isEqualTo(now.plus(15, ChronoUnit.SECONDS));
    }

    @Test
    void turnOpenAtAccountsForCardRevealOnOpeningTurn() {
        Instant now = Instant.now();
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), UUID.randomUUID(), now,
            now.plus(AuctionTiming.ROUND_DURATION_SECONDS, ChronoUnit.SECONDS),
            UUID.randomUUID(), RoundStatus.OPEN
        );

        Instant openAt = AuctionRules.computeTurnOpenAt(round);
        assertThat(openAt).isEqualTo(
            now.plus(AuctionTiming.ROUND_REVEAL_SECONDS + AuctionTiming.TURN_OPEN_AFTER_SECONDS, ChronoUnit.SECONDS)
        );
    }

    @Test
    void allNonWinnersHavePassedRequiresEveryOtherPlayerToPass() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();
        ActiveSession session = fourPlayerSession(List.of(u1, u2, u3, u4));
        ActivePlayer winner = session.findPlayerByUserId(u1).orElseThrow();
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plus(30, ChronoUnit.SECONDS),
            u1, RoundStatus.OPEN
        );
        round.getBids().add(new ActiveBid(UUID.randomUUID(), winner.getId(), 5L, UUID.randomUUID(), Instant.now()));
        round.registerBid(winner.getId(), 5L, round.getEndsAt(), u2);

        assertThat(AuctionRules.allNonWinnersHavePassed(session, round)).isFalse();

        round.registerPass(u2);
        assertThat(AuctionRules.allNonWinnersHavePassed(session, round)).isFalse();

        round.registerPass(u3);
        round.registerPass(u4);
        assertThat(AuctionRules.allNonWinnersHavePassed(session, round)).isTrue();
    }

    @Test
    void maySettleLeadingRoundRequiresAllNonWinnersToPassInFourPlayerMode() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();
        ActiveSession session = fourPlayerSession(List.of(u1, u2, u3, u4));
        ActivePlayer p3 = session.findPlayerByUserId(u3).orElseThrow();
        ActivePlayer p4 = session.findPlayerByUserId(u4).orElseThrow();
        Card card = new Card(UUID.randomUUID(), "Striker", Position.FWD, 80);
        Map<UUID, Card> cardMap = Map.of(card.getId(), card);
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), card.getId(), Instant.now(), Instant.now().plus(30, ChronoUnit.SECONDS),
            u1, RoundStatus.OPEN
        );
        round.registerPass(u1);
        round.registerPass(u2);
        round.getBids().add(new ActiveBid(UUID.randomUUID(), p3.getId(), 3L, UUID.randomUUID(), Instant.now()));
        round.registerBid(p3.getId(), 3L, round.getEndsAt(), u4);
        round.getBids().add(new ActiveBid(UUID.randomUUID(), p4.getId(), 4L, UUID.randomUUID(), Instant.now()));
        round.registerBid(p4.getId(), 4L, round.getEndsAt(), u1);

        assertThat(AuctionRules.maySettleLeadingRound(session, round, card, cardMap)).isFalse();

        round.registerPass(u3);
        assertThat(AuctionRules.maySettleLeadingRound(session, round, card, cardMap)).isTrue();
    }

    @Test
    void maySettleLeadingRoundSettlesImmediatelyWhenNoCounterBidPossible() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();
        ActiveSession session = fourPlayerSession(List.of(u1, u2, u3, u4));
        ActivePlayer winner = session.findPlayerByUserId(u1).orElseThrow();
        ActivePlayer p2 = session.findPlayerByUserId(u2).orElseThrow();
        ActivePlayer p3 = session.findPlayerByUserId(u3).orElseThrow();
        ActivePlayer p4 = session.findPlayerByUserId(u4).orElseThrow();

        Map<UUID, Card> cardMap = new java.util.HashMap<>();
        for (ActivePlayer blocked : List.of(p2, p3, p4)) {
            for (int i = 0; i < 4; i++) {
                Card defCard = new Card(UUID.randomUUID(), "Def " + blocked.getNickname() + i, Position.DEF, 70);
                cardMap.put(defCard.getId(), defCard);
                blocked.addCard(defCard.getId(), Position.DEF);
            }
        }
        Card auctionCard = new Card(UUID.randomUUID(), "Solo DEF", Position.DEF, 80);
        cardMap.put(auctionCard.getId(), auctionCard);

        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), auctionCard.getId(), Instant.now(), Instant.now().plus(30, ChronoUnit.SECONDS),
            u1, RoundStatus.OPEN
        );
        round.getBids().add(new ActiveBid(UUID.randomUUID(), winner.getId(), 1L, UUID.randomUUID(), Instant.now()));
        round.registerBid(winner.getId(), 1L, round.getEndsAt(), u2);

        assertThat(AuctionRules.anyOtherPlayerCanBid(session, round, auctionCard, cardMap, u1)).isFalse();
        assertThat(AuctionRules.maySettleLeadingRound(session, round, auctionCard, cardMap)).isTrue();
    }

    @Test
    void findNextEligibleBidderSkipsCurrentHighestBidder() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();
        ActiveSession session = fourPlayerSession(List.of(u1, u2, u3, u4));
        ActivePlayer p3 = session.findPlayerByUserId(u3).orElseThrow();
        ActivePlayer p4 = session.findPlayerByUserId(u4).orElseThrow();
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plus(30, ChronoUnit.SECONDS),
            u1, RoundStatus.OPEN
        );
        round.registerPass(u1);
        round.registerPass(u2);
        round.registerBid(p3.getId(), 3L, round.getEndsAt(), u4);
        round.registerBid(p4.getId(), 4L, round.getEndsAt(), u1);

        Card card = new Card(UUID.randomUUID(), "Striker", Position.FWD, 80);
        Map<UUID, Card> cardMap = Map.of(card.getId(), card);
        round.setCardId(card.getId());

        assertThat(AuctionRules.findNextEligibleBidder(session, round, card, cardMap, u4))
            .contains(u3);
        assertThat(AuctionRules.findNextEligibleBidder(session, round, card, cardMap, u1))
            .contains(u3);
    }

    @Test
    void findNextNonPassedParticipantGivesOutbidChanceAfterTwoPassesAndTwoBids() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();
        ActiveSession session = fourPlayerSession(List.of(u1, u2, u3, u4));
        ActivePlayer p3 = session.findPlayerByUserId(u3).orElseThrow();
        ActivePlayer p4 = session.findPlayerByUserId(u4).orElseThrow();
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plus(30, ChronoUnit.SECONDS),
            u1, RoundStatus.OPEN
        );
        round.registerPass(u1);
        round.registerPass(u2);
        round.registerBid(p3.getId(), 3L, round.getEndsAt(), u4);
        round.registerBid(p4.getId(), 4L, round.getEndsAt(), u1);

        assertThat(AuctionRules.findNextNonPassedParticipant(session, round, u1, true))
            .contains(u3);
    }

    @Test
    void hasFullLobbyUsesMaxPlayers() {
        ActiveSession session = fourPlayerSession(List.of(UUID.randomUUID(), UUID.randomUUID()));
        session.setMaxPlayers(4);
        assertThat(AuctionRules.hasFullLobby(session)).isFalse();

        session.getPlayers().add(player(UUID.randomUUID(), "p3"));
        session.getPlayers().add(player(UUID.randomUUID(), "p4"));
        assertThat(AuctionRules.hasFullLobby(session)).isTrue();
    }

    private static ActiveSession fourPlayerSession(List<UUID> turnOrder) {
        ActiveSession session = new ActiveSession();
        session.setId(UUID.randomUUID());
        session.setStatus(SessionStatus.AUCTIONING);
        session.setGameMode(GameMode.TOURNAMENT);
        session.setMaxPlayers(4);
        session.setTurnOrder(turnOrder);
        int seat = 0;
        for (UUID uid : turnOrder) {
            session.getPlayers().add(player(uid, "p" + seat++));
        }
        return session;
    }

    private static ActivePlayer player(UUID userId, String nick) {
        ActivePlayer p = new ActivePlayer();
        p.setId(UUID.randomUUID());
        p.setUserId(userId);
        p.setNickname(nick);
        p.setFormation(Formation.FOUR_FOUR_TWO);
        p.setRemainingBudgetCents(10_000L);
        return p;
    }
}
