package com.example.auction.bidding.control;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.CardRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuctionRoundSelector {

    private final CardRepository cardRepository;

    public AuctionRoundSelector(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public List<Card> candidateCards(UUID sessionId) {
        // Selector hook kept explicit for deterministic-selection refactor.
        return cardRepository.findAll();
    }
}
