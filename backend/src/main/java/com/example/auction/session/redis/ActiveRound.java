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
    private Instant turnStartedAt;
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
        this.turnStartedAt = startsAt;
        this.status = status;
    }

    // --- Domain methods (mirror JPA AuctionRound) ---

    public void registerBid(UUID bidderId, long amountCents, Instant newEndsAt, UUID nextTurnUserId) {
        this.highestBidderId = bidderId;
        this.highestBidCents = amountCents;
        this.endsAt = newEndsAt;
        this.currentTurnUserId = nextTurnUserId;
        this.turnStartedAt = Instant.now();
        this.readyUserOneId = null;
        this.readyUserTwoId = null;
        if (readyUserIds != null) {
            readyUserIds.clear();
        }
    }

    public void markSold() {
        this.status = RoundStatus.SOLD;
    }

    public void markPassed() {
        this.status = RoundStatus.PASSED;
    }

    private List<UUID> readyUserIds = new ArrayList<>();

    public boolean registerReady(UUID userId) {
        if (userId == null) {
            return false;
        }
        syncReadyFromLegacy();
        if (readyUserIds.contains(userId)) {
            return false;
        }
        readyUserIds.add(userId);
        syncLegacyFromReady();
        return true;
    }

    @JsonIgnore
    public boolean areAllReady(int requiredPlayers) {
        syncReadyFromLegacy();
        return readyUserIds.size() >= requiredPlayers;
    }

    /** @deprecated use {@link #areAllReady(int)} */
    @JsonIgnore
    public boolean areBothReady() {
        return areAllReady(2);
    }

    private void syncReadyFromLegacy() {
        if (readyUserIds == null) {
            readyUserIds = new ArrayList<>();
        }
        if (readyUserOneId != null && !readyUserIds.contains(readyUserOneId)) {
            readyUserIds.add(readyUserOneId);
        }
        if (readyUserTwoId != null && !readyUserIds.contains(readyUserTwoId)) {
            readyUserIds.add(readyUserTwoId);
        }
    }

    private void syncLegacyFromReady() {
        readyUserOneId = readyUserIds.size() > 0 ? readyUserIds.get(0) : null;
        readyUserTwoId = readyUserIds.size() > 1 ? readyUserIds.get(1) : null;
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
    public void setCurrentTurnUserId(UUID currentTurnUserId) {
        this.currentTurnUserId = currentTurnUserId;
        this.turnStartedAt = Instant.now();
    }
    public Instant getTurnStartedAt() { return turnStartedAt; }
    public void setTurnStartedAt(Instant turnStartedAt) { this.turnStartedAt = turnStartedAt; }
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
    public List<UUID> getReadyUserIds() { return readyUserIds; }
    public void setReadyUserIds(List<UUID> readyUserIds) {
        this.readyUserIds = readyUserIds != null ? new ArrayList<>(readyUserIds) : new ArrayList<>();
    }
}
