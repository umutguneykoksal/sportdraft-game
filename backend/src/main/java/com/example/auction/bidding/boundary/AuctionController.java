package com.example.auction.bidding.boundary;

import com.example.auction.bidding.control.AuctionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
public class AuctionController {

    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @PostMapping("/auction/rounds/start")
    public AuctionRoundResponse startRound(@PathVariable UUID sessionId) {
        return auctionService.startRound(sessionId);
    }

    @PostMapping("/bids")
    public AuctionRoundResponse placeBid(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PlaceBidRequest request
    ) {
        UUID userId = extractUserId(authorization);
        return auctionService.placeBid(sessionId, userId, idempotencyKey, request);
    }

    @PostMapping("/auction/rounds/{roundId}/ready-next")
    public AuctionRoundResponse readyNext(
        @PathVariable UUID sessionId,
        @PathVariable UUID roundId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = extractUserId(authorization);
        return auctionService.readyNext(sessionId, userId, roundId);
    }

    @PostMapping("/auction/rounds/{roundId}/pass")
    public AuctionRoundResponse passRound(
        @PathVariable UUID sessionId,
        @PathVariable UUID roundId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = extractUserId(authorization);
        return auctionService.passRound(sessionId, userId, roundId);
    }

    private UUID extractUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization: Bearer <uuid>");
        }
        try {
            return UUID.fromString(authorization.substring("Bearer ".length()).trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token format");
        }
    }
}
