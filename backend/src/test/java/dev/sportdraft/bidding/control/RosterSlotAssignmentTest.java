package dev.sportdraft.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.redis.ActivePlayer;
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
    void prefersPrimaryForForwardWhoAlsoPlaysCam() {
        UUID salahId = UUID.fromString("44444444-4444-4444-4444-444444444002");
        Card salah = card(salahId, "Salah", Position.FWD, Set.of(Position.FWD, Position.CAM));

        ActivePlayer player = new ActivePlayer(
            UUID.randomUUID(), UUID.randomUUID(), "P1", Formation.FOUR_TWO_THREE_ONE, 100
        );
        Map<UUID, Card> cardMap = Map.of(salahId, salah);

        assertThat(RosterSlotAssignment.resolveAssignment(player, salah, cardMap))
            .contains(Position.FWD);
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
