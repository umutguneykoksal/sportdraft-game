package dev.sportdraft.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.bidding.entity.RoundStatus;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
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
class TurnSkipResolverTest {

    @Mock
    private AuctionSettlementService settlement;

    private TurnSkipResolver turnSkipResolver;

    @BeforeEach
    void setUp() {
        turnSkipResolver = new TurnSkipResolver(settlement);
    }

    @Test
    void returnsFalseWhenCurrentPlayerCanBid() {
        UUID userId = UUID.randomUUID();
        ActiveSession session = sessionWithPlayer(userId, 50);
        Card card = card(Position.FWD, 70);
        ActiveRound round = openRound(userId, card.getId());
        Map<UUID, Card> cardMap = Map.of(card.getId(), card);

        boolean settled = turnSkipResolver.skipInactiveTurns(round, session, cardMap);

        assertThat(settled).isFalse();
        assertThat(round.getCurrentTurnUserId()).isEqualTo(userId);
        verify(settlement, never()).settleRoundInline(round, session, cardMap);
    }

    @Test
    void marksRoundPassedWhenNoEligibleBidderAndNoLeadingBid() {
        UUID userId = UUID.randomUUID();
        ActiveSession session = sessionWithPlayer(userId, 0);
        Card card = card(Position.FWD, 90);
        ActiveRound round = openRound(userId, card.getId());
        Map<UUID, Card> cardMap = Map.of(card.getId(), card);

        boolean settled = turnSkipResolver.skipInactiveTurns(round, session, cardMap);

        assertThat(settled).isTrue();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.PASSED);
    }

    private static ActiveSession sessionWithPlayer(UUID userId, long budget) {
        ActiveSession session = new ActiveSession(UUID.randomUUID(), SessionStatus.AUCTIONING, Instant.now(), GameMode.DUEL);
        session.setTurnOrder(List.of(userId));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), userId, "P1", Formation.FOUR_FOUR_TWO, budget));
        return session;
    }

    private static ActiveRound openRound(UUID turnUserId, UUID cardId) {
        return new ActiveRound(
            UUID.randomUUID(),
            cardId,
            Instant.now(),
            Instant.now().plus(30, ChronoUnit.SECONDS),
            turnUserId,
            RoundStatus.OPEN
        );
    }

    private static Card card(Position position, int rating) {
        return new Card(UUID.randomUUID(), "Test", position, rating);
    }
}
