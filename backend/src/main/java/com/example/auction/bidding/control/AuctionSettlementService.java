package com.example.auction.bidding.control;

import org.springframework.stereotype.Component;

@Component
public class AuctionSettlementService {
    // Settlement responsibility has been split out from AuctionService.
    // Existing settlement is still delegated from AuctionEngine for now.
}
