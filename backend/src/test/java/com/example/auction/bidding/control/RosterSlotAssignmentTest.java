package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.Position;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.redis.ActivePlayer;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RosterSlotAssignmentTest {

    @Test
    void assignsVersatileCardToSecondaryWhenPrimaryFull() {
        UUID trentId = UUID.fromString("22222222-2222-2222-2222-222222222003");
        Card trent = card(trentId, "Trent", Position.DEF, Set.of(Position.DEF, Position.CM));

        ActivePlayer player = new ActivePlayer(
            UUID.randomUUID(), UUID.randomUUID(), "P1", Formation.FOUR_FOUR_TWO, 100
        );
        for (int i = 0; i < 4; i++) {
            UUID defId = UUID.randomUUID();
            player.addCard(defId, Position.DEF);
        }

        Map<UUID, Card> cardMap = Map.of(trentId, trent);
        assertThat(RosterSlotAssignment.resolveAssignment(player, trent, cardMap))
            .contains(Position.CM);
    }

    @Test
    void prefersPrimaryPositionWhenBothOpen() {
        UUID trentId = UUID.fromString("22222222-2222-2222-2222-222222222003");
        Card trent = card(trentId, "Trent", Position.DEF, Set.of(Position.DEF, Position.CM));

        ActivePlayer player = new ActivePlayer(
            UUID.randomUUID(), UUID.randomUUID(), "P1", Formation.FOUR_FOUR_TWO, 100
        );
        Map<UUID, Card> cardMap = Map.of(trentId, trent);

        assertThat(RosterSlotAssignment.resolveAssignment(player, trent, cardMap))
            .contains(Position.DEF);
    }

    @Test
    void cannotFillWhenAllEligibleSlotsFull() {
        UUID trentId = UUID.fromString("22222222-2222-2222-2222-222222222003");
        Card trent = card(trentId, "Trent", Position.DEF, Set.of(Position.DEF, Position.CM));

        ActivePlayer player = new ActivePlayer(
            UUID.randomUUID(), UUID.randomUUID(), "P1", Formation.FOUR_FOUR_TWO, 100
        );
        for (int i = 0; i < 4; i++) {
            player.addCard(UUID.randomUUID(), Position.DEF);
        }
        for (int i = 0; i < 4; i++) {
            player.addCard(UUID.randomUUID(), Position.CM);
        }

        assertThat(RosterSlotAssignment.canFillCard(player, trent, Map.of(trentId, trent))).isFalse();
    }

    private static Card card(UUID id, String name, Position primary, Set<Position> eligible) {
        return new Card(id, name, primary, 85, eligible);
    }
}
