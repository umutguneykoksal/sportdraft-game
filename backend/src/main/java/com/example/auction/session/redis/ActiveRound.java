package com.example.auction.session.redis;

import com.example.auction.bidding.entity.RoundStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActiveRound {

    private UUID id;
    private UUID cardId;
    private Instant startsAt;
    private Instant endsAt;
    private long highestBidCents;
    private UUID highestBidderId;
    private UUID openingBidderUserId;
    private UUID currentTurnUserId;
    private UUID readyUserOneId;
    private UUID readyUserTwoId;
    private RoundStatus status;
    private List<ActiveBid> bids = new ArrayList<>();
    private List<UUID> passedUserIds = new ArrayList<>();

    public ActiveRound() {
    }

    public ActiveRound(UUID id, UUID cardId, Instant startsAt, Instant endsAt,
                       UUID openingBidderUserId, RoundStatus status) {
        this.id = id;
        this.cardId = cardId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.openingBidderUserId = openingBidderUserId;
        this.currentTurnUserId = openingBidderUserId;
        this.status = status;
    }

    // --- Domain methods (mirror JPA AuctionRound) ---

    public void registerBid(UUID bidderId, long amountCents, Instant newEndsAt, UUID nextTurnUserId) {
        this.highestBidderId = bidderId;
        this.highestBidCents = amountCents;
        this.endsAt = newEndsAt;
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
        if (readyUserOneId == null) { readyUserOneId = userId; return true; }
        if (readyUserTwoId == null) { readyUserTwoId = userId; return true; }
        return false;
    }

    @JsonIgnore
    public boolean areBothReady() {
        return readyUserOneId != null && readyUserTwoId != null;
    }

    public boolean registerPass(UUID userId) {
        if (userId == null || passedUserIds.contains(userId)) return false;
        passedUserIds.add(userId);
        return true;
    }

    public boolean hasUserPassed(UUID userId) {
        return passedUserIds.contains(userId);
    }

    @JsonIgnore
    public boolean haveBothPassed() {
        return passedUserIds.size() >= 2;
    }

    public boolean hasBidWithIdempotencyKey(UUID idempotencyKey) {
        return bids.stream().anyMatch(b -> b.getIdempotencyKey().equals(idempotencyKey));
    }

    // --- Getters / Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCardId() { return cardId; }
    public void setCardId(UUID cardId) { this.cardId = cardId; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public long getHighestBidCents() { return highestBidCents; }
    public void setHighestBidCents(long highestBidCents) { this.highestBidCents = highestBidCents; }
    public UUID getHighestBidderId() { return highestBidderId; }
    public void setHighestBidderId(UUID highestBidderId) { this.highestBidderId = highestBidderId; }
    public UUID getOpeningBidderUserId() { return openingBidderUserId; }
    public void setOpeningBidderUserId(UUID openingBidderUserId) { this.openingBidderUserId = openingBidderUserId; }
    public UUID getCurrentTurnUserId() { return currentTurnUserId; }
    public void setCurrentTurnUserId(UUID currentTurnUserId) { this.currentTurnUserId = currentTurnUserId; }
    public UUID getReadyUserOneId() { return readyUserOneId; }
    public void setReadyUserOneId(UUID readyUserOneId) { this.readyUserOneId = readyUserOneId; }
    public UUID getReadyUserTwoId() { return readyUserTwoId; }
    public void setReadyUserTwoId(UUID readyUserTwoId) { this.readyUserTwoId = readyUserTwoId; }
    public RoundStatus getStatus() { return status; }
    public void setStatus(RoundStatus status) { this.status = status; }
    public List<ActiveBid> getBids() { return bids; }
    public void setBids(List<ActiveBid> bids) { this.bids = bids; }
    public List<UUID> getPassedUserIds() { return passedUserIds; }
    public void setPassedUserIds(List<UUID> passedUserIds) { this.passedUserIds = passedUserIds; }
}
