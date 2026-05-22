package dev.sportdraft.bidding.control;

import dev.sportdraft.bidding.entity.Card;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuctionRoundSelector {

    private final CardCatalogService cardCatalog;

    public AuctionRoundSelector(CardCatalogService cardCatalog) {
        this.cardCatalog = cardCatalog;
    }

    public List<Card> candidateCards(UUID sessionId) {
        return cardCatalog.getAllCards();
    }
}
