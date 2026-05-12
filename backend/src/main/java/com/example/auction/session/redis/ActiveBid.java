package com.example.auction.session.redis;

import java.time.Instant;
import java.util.UUID;

public class ActiveBid {

    private UUID id;
    private UUID bidderId;
    private long amountCents;
    private UUID idempotencyKey;
    private Instant placedAt;

    public ActiveBid() {
    }

    public ActiveBid(UUID id, UUID bidderId, long amountCents, UUID idempotencyKey, Instant placedAt) {
        this.id = id;
        this.bidderId = bidderId;
        this.amountCents = amountCents;
        this.idempotencyKey = idempotencyKey;
        this.placedAt = placedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getBidderId() { return bidderId; }
    public void setBidderId(UUID bidderId) { this.bidderId = bidderId; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getPlacedAt() { return placedAt; }
    public void setPlacedAt(Instant placedAt) { this.placedAt = placedAt; }
}
