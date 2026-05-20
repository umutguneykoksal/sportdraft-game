package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import java.util.List;
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
        p.setUserId(userId);
        p.setNickname(nick);
        p.setFormation(Formation.FOUR_FOUR_TWO);
        p.setRemainingBudgetCents(10_000L);
        return p;
    }
}
