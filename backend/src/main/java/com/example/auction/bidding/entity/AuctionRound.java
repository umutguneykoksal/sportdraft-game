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

    @Column(name = "opening_bidder_user_id")
    private UUID openingBidderUserId;

    @Column(name = "current_turn_user_id")
    private UUID currentTurnUserId;

    @Column(name = "ready_user_one_id")
    private UUID readyUserOneId;

    @Column(name = "ready_user_two_id")
    private UUID readyUserTwoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoundStatus status;

    @Version
    private Long version;

    protected AuctionRound() {
    }

    public AuctionRound(
        UUID id,
        GameSession session,
        Card card,
        Instant startsAt,
        Instant endsAt,
        UUID openingBidderUserId,
        RoundStatus status
    ) {
        this.id = id;
        this.session = session;
        this.card = card;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.openingBidderUserId = openingBidderUserId;
        this.currentTurnUserId = openingBidderUserId;
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

    public UUID getOpeningBidderUserId() {
        return openingBidderUserId;
    }

    public UUID getCurrentTurnUserId() {
        return currentTurnUserId;
    }

    public void setCurrentTurnUserId(UUID currentTurnUserId) {
        this.currentTurnUserId = currentTurnUserId;
    }

    public RoundStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public void registerBid(UUID bidderId, long amountCents, Instant endsAt, UUID nextTurnUserId) {
        this.highestBidderId = bidderId;
        this.highestBidCents = amountCents;
        this.endsAt = endsAt;
        this.currentTurnUserId = nextTurnUserId;
        this.readyUserOneId = null;
        this.readyUserTwoId = null;
    }

    public void markSold() {
        this.status = RoundStatus.SOLD;
    }

    public void markPassed() {
        this.status = RoundStatus.PASSED;
    }

    public boolean registerReady(UUID userId) {
        if (userId == null || userId.equals(readyUserOneId) || userId.equals(readyUserTwoId)) {
            return false;
        }
        if (readyUserOneId == null) {
            readyUserOneId = userId;
            return true;
        }
        if (readyUserTwoId == null) {
            readyUserTwoId = userId;
            return true;
        }
        return false;
    }

    public boolean areBothReady() {
        return readyUserOneId != null && readyUserTwoId != null;
    }

    public UUID getReadyUserOneId() {
        return readyUserOneId;
    }

    public UUID getReadyUserTwoId() {
        return readyUserTwoId;
    }
}
