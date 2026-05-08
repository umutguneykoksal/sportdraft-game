package com.example.auction.bidding.boundary;

import com.example.auction.bidding.control.AuctionEngine;
import com.example.auction.common.web.AuthTokenResolver;
import com.example.auction.common.web.CommandIdempotencyGuard;
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
@RequestMapping("/api/v2/sessions/{sessionId}")
public class AuctionCommandController {

    private final AuctionEngine auctionEngine;
    private final AuthTokenResolver authTokenResolver;
    private final CommandIdempotencyGuard commandIdempotencyGuard;

    public AuctionCommandController(
        AuctionEngine auctionEngine,
        AuthTokenResolver authTokenResolver,
        CommandIdempotencyGuard commandIdempotencyGuard
    ) {
        this.auctionEngine = auctionEngine;
        this.authTokenResolver = authTokenResolver;
        this.commandIdempotencyGuard = commandIdempotencyGuard;
    }

    @PostMapping("/auction/rounds/start")
    public AuctionRoundResponse startRound(@PathVariable UUID sessionId) {
        return auctionEngine.startRound(sessionId);
    }

    @GetMapping("/auction/state")
    public AuctionStateResponse getAuctionState(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return auctionEngine.getAuctionState(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @PostMapping("/bids")
    public AuctionRoundResponse placeBid(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PlaceBidRequest request
    ) {
        commandIdempotencyGuard.requireUuid(idempotencyKey);
        return auctionEngine.placeBid(
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
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        commandIdempotencyGuard.requireUuid(idempotencyKey);
        return auctionEngine.readyNext(sessionId, authTokenResolver.resolveUserId(authorization), roundId);
    }

    @PostMapping("/auction/rounds/{roundId}/pass")
    public AuctionRoundResponse passRound(
        @PathVariable UUID sessionId,
        @PathVariable UUID roundId,
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        commandIdempotencyGuard.requireUuid(idempotencyKey);
        return auctionEngine.passRound(sessionId, authTokenResolver.resolveUserId(authorization), roundId);
    }
}
