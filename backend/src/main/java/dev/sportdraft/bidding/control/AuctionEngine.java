package dev.sportdraft.bidding.control;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.boundary.AuctionStateResponse;
import dev.sportdraft.bidding.boundary.PlaceBidRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuctionEngine {

    private final AuctionService auctionService;

    public AuctionEngine(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    public AuctionRoundResponse startRound(UUID sessionId, UUID userId) {
        return auctionService.startRound(sessionId, userId);
    }

    public AuctionRoundResponse placeBid(UUID sessionId, UUID userId, String idempotencyKey, PlaceBidRequest request) {
        return auctionService.placeBid(sessionId, userId, idempotencyKey, request);
    }

    public AuctionRoundResponse readyNext(UUID sessionId, UUID userId, UUID roundId) {
        return auctionService.readyNext(sessionId, userId, roundId);
    }

    public AuctionRoundResponse passRound(UUID sessionId, UUID userId, UUID roundId) {
        return auctionService.passRound(sessionId, userId, roundId);
    }

    public AuctionStateResponse getAuctionState(UUID sessionId, UUID userId) {
        return auctionService.getAuctionState(sessionId, userId);
    }
}
