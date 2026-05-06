package com.example.auction.bidding.entity;

import com.example.auction.session.entity.SessionPlayer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bid")
public class Bid {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private AuctionRound round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    private SessionPlayer bidder;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    protected Bid() {
    }

    public Bid(UUID id, AuctionRound round, SessionPlayer bidder, long amountCents, UUID idempotencyKey, Instant placedAt) {
        this.id = id;
        this.round = round;
        this.bidder = bidder;
        this.amountCents = amountCents;
        this.idempotencyKey = idempotencyKey;
        this.placedAt = placedAt;
    }

    public UUID getId() {
        return id;
    }

    public AuctionRound getRound() {
        return round;
    }

    public SessionPlayer getBidder() {
        return bidder;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }
}
