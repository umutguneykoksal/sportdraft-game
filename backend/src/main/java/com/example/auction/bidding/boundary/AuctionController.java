package com.example.auction.bidding.boundary;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.common.web.AuthTokenResolver;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
public class AuctionController {

    private final AuctionService auctionService;
    private final AuthTokenResolver authTokenResolver;

    public AuctionController(AuctionService auctionService, AuthTokenResolver authTokenResolver) {
        this.auctionService = auctionService;
        this.authTokenResolver = authTokenResolver;
    }

    @PostMapping("/auction/rounds/start")
    public AuctionRoundResponse startRound(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return auctionService.startRound(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @GetMapping("/auction/state")
    public AuctionStateResponse getAuctionState(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return auctionService.getAuctionState(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @PostMapping("/bids")
    public AuctionRoundResponse placeBid(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PlaceBidRequest request
    ) {
        return auctionService.placeBid(
            sessionId,
            authTokenResolver.resolveUserId(authorization),
            idempotencyKey,
            request
        );
    }

    @PostMapping("/auction/rounds/{roundId}/ready-next")
    public AuctionRoundResponse readyNext(
        @PathVariable UUID sessionId,
        @PathVariable UUID roundId,
        @RequestHeader("Authorization") String authorization
    ) {
        return auctionService.readyNext(sessionId, authTokenResolver.resolveUserId(authorization), roundId);
    }

    @PostMapping("/auction/rounds/{roundId}/pass")
    public AuctionRoundResponse passRound(
        @PathVariable UUID sessionId,
        @PathVariable UUID roundId,
        @RequestHeader("Authorization") String authorization
    ) {
        return auctionService.passRound(sessionId, authTokenResolver.resolveUserId(authorization), roundId);
    }
}
