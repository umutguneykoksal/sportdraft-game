package com.example.auction.bidding.entity;

import com.example.auction.session.entity.GameSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auction_round")
public class AuctionRound {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "highest_bid_cents", nullable = false)
    private long highestBidCents;

    @Column(name = "highest_bidder_id")
    private UUID highestBidderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoundStatus status;

    @Version
    private Long version;

    protected AuctionRound() {
    }

    public AuctionRound(UUID id, GameSession session, Card card, Instant startsAt, Instant endsAt, RoundStatus status) {
        this.id = id;
        this.session = session;
        this.card = card;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public GameSession getSession() {
        return session;
    }

    public Card getCard() {
        return card;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public long getHighestBidCents() {
        return highestBidCents;
    }

    public UUID getHighestBidderId() {
        return highestBidderId;
    }

    public RoundStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public void registerBid(UUID bidderId, long amountCents, Instant endsAt) {
        this.highestBidderId = bidderId;
        this.highestBidCents = amountCents;
        this.endsAt = endsAt;
    }

    public void markSold() {
        this.status = RoundStatus.SOLD;
    }

    public void markPassed() {
        this.status = RoundStatus.PASSED;
    }
}
