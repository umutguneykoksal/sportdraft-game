package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.CardRepository;
import com.example.auction.bidding.entity.Position;
import com.example.auction.session.entity.PoolLeague;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardCatalogServiceTest {

    @Mock
    private CardRepository cardRepository;

    private CardCatalogService cardCatalogService;

    @BeforeEach
    void setUp() {
        cardCatalogService = new CardCatalogService(cardRepository);
    }

    @Test
    void countCardsForPool_filtersByLeagueAndRating() {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 110; i++) {
            cards.add(card(PoolLeague.PREMIER_LEAGUE, 70 + (i % 15)));
        }
        for (int i = 0; i < 25; i++) {
            cards.add(card(PoolLeague.SUPER_LIG, 80 + (i % 5)));
        }
        when(cardRepository.findAll()).thenReturn(cards);
        cardCatalogService.init();

        assertThat(cardCatalogService.countCardsForPool(PoolLeague.PREMIER_LEAGUE, 70)).isEqualTo(110);
        assertThat(cardCatalogService.countCardsForPool(PoolLeague.SUPER_LIG, 80)).isEqualTo(25);
        assertThat(cardCatalogService.countCardsForPool(PoolLeague.SUPER_LIG, 85)).isLessThan(25);
    }

    private static Card card(PoolLeague league, int rating) {
        Card card = new Card(UUID.randomUUID(), "Player", Position.FWD, rating, Set.of(Position.FWD));
        try {
            var field = Card.class.getDeclaredField("poolLeague");
            field.setAccessible(true);
            field.set(card, league);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return card;
    }
}
