package com.example.auction.bidding.control;

import com.example.auction.session.entity.PoolLeague;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.CardRepository;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-memory cache of the card master data loaded once at startup.
 * Cards are static reference data that never changes at runtime.
 */
@Service
public class CardCatalogService {

    private final CardRepository cardRepository;
    private Map<UUID, Card> cardMap;
    private List<Card> allCards;

    public CardCatalogService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    @PostConstruct
    void init() {
        List<Card> cards = cardRepository.findAll();
        this.allCards = Collections.unmodifiableList(cards);
        this.cardMap = cards.stream()
            .collect(Collectors.toUnmodifiableMap(Card::getId, Function.identity()));
    }

    public Map<UUID, Card> getCardMap() {
        return cardMap;
    }

    public List<Card> getAllCards() {
        return allCards;
    }

    public List<UUID> getAllCardIds() {
        return allCards.stream().map(Card::getId).toList();
    }

    public List<UUID> getCardIdsWithMinRating(int minRating) {
        return getCardIdsForPool(PoolLeague.PREMIER_LEAGUE, minRating);
    }

    public List<UUID> getCardIdsForPool(PoolLeague league, int minRating) {
        PoolLeague effectiveLeague = PoolLeague.effective(league);
        return allCards.stream()
            .filter(card -> card.getPoolLeague() == effectiveLeague && card.getRating() >= minRating)
            .map(Card::getId)
            .toList();
    }

    public int countCardsForPool(PoolLeague league, int minRating) {
        return getCardIdsForPool(league, minRating).size();
    }

    public Card getCard(UUID cardId) {
        Card card = cardMap.get(cardId);
        if (card == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found: " + cardId);
        }
        return card;
    }
}
