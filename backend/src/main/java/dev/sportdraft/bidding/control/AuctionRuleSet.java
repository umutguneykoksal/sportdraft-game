package dev.sportdraft.bidding.control;

import org.springframework.stereotype.Component;

@Component
public class AuctionRuleSet {

    public long minIncrementCents() {
        return 1L;
    }

    public long startingBidCents() {
        return 1L;
    }

    public int rosterSize() {
        return 11;
    }
}
