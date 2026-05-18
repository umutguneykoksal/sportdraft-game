package com.example.auction.bidding.control;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.Position;
import com.example.auction.session.redis.ActivePlayer;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves which formation slot a versatile card fills when won at auction.
 * Prefers the card's primary position when that slot is open.
 */
public final class RosterSlotAssignment {

    private static final EnumSet<Position> ASSIGNMENT_ORDER = EnumSet.allOf(Position.class);

    private RosterSlotAssignment() {
    }

    public static boolean canFillCard(ActivePlayer player, Card card, Map<UUID, Card> cardMap) {
        return resolveAssignment(player, card, cardMap).isPresent();
    }

    public static Optional<Position> resolveAssignment(ActivePlayer player, Card card, Map<UUID, Card> cardMap) {
        if (player.isRosterFull()) {
            return Optional.empty();
        }
        for (Position pos : ASSIGNMENT_ORDER) {
            if (!card.canPlay(pos)) {
                continue;
            }
            if (openSlotsForPosition(player, pos, cardMap) > 0) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    public static int openSlotsForPosition(ActivePlayer player, Position position, Map<UUID, Card> cardMap) {
        int required = player.getFormation().slotCount(position);
        if (required == 0) {
            return 0;
        }
        long filled = player.getCardIds().stream()
            .filter(cardId -> assignedPosition(player, cardId, cardMap) == position)
            .count();
        return Math.max(required - (int) filled, 0);
    }

    public static int totalOpenSlots(ActivePlayer player, Map<UUID, Card> cardMap) {
        int total = 0;
        for (Position pos : Position.values()) {
            total += openSlotsForPosition(player, pos, cardMap);
        }
        return total;
    }

    public static Position assignedPosition(ActivePlayer player, UUID cardId, Map<UUID, Card> cardMap) {
        Position assigned = player.getCardAssignments().get(cardId);
        if (assigned != null) {
            return assigned;
        }
        Card card = cardMap.get(cardId);
        return card != null ? card.getPosition() : null;
    }
}
