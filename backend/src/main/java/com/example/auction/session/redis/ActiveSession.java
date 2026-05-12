package com.example.auction.session.redis;

import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.session.entity.SessionStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root for an active game session stored in Redis.
 * Combines what was previously GameSession + SessionPlayer + AuctionRound + Bid
 * into a single document for atomic reads/writes.
 */
public class ActiveSession {

    private UUID id;
    private SessionStatus status;
    private Instant countdownEndsAt;
    private Long matchSeed;
    private Instant matchStartDeadlineAt;
    private UUID matchStartReadyUserOneId;
    private UUID matchStartReadyUserTwoId;
    private Instant createdAt;
    private long version;
    private Integer homeScore;
    private Integer awayScore;
    private List<ActivePlayer> players = new ArrayList<>();
    private List<ActiveRound> rounds = new ArrayList<>();
    private List<UUID> cardPool = new ArrayList<>();
    private int cardPoolIndex;

    public ActiveSession() {
    }

    public ActiveSession(UUID id, SessionStatus status, Instant createdAt) {
        this.id = id;
        this.status = status;
        this.createdAt = createdAt;
        this.version = 0;
    }

    // --- State machine transitions (mirror JPA GameSession) ---

    public void moveToCountdown(Instant endsAt) {
        this.status = SessionStatus.COUNTDOWN;
        this.countdownEndsAt = endsAt;
    }

    public void moveToFormationSelect(Instant endsAt) {
        this.status = SessionStatus.FORMATION_SELECT;
        this.countdownEndsAt = endsAt;
    }

    public void moveToAuctioning() {
        this.status = SessionStatus.AUCTIONING;
        this.countdownEndsAt = null;
        this.matchStartDeadlineAt = null;
        this.matchStartReadyUserOneId = null;
        this.matchStartReadyUserTwoId = null;
    }

    public void moveToAuctioning(List<UUID> shuffledCardPool) {
        moveToAuctioning();
        this.cardPool = new ArrayList<>(shuffledCardPool);
        this.cardPoolIndex = 0;
    }

    public void moveToWaiting() {
        this.status = SessionStatus.WAITING;
        this.countdownEndsAt = null;
    }

    public void moveToFinalizing(long seed) {
        this.status = SessionStatus.FINALIZING;
        this.matchSeed = seed;
        this.countdownEndsAt = null;
        this.matchStartDeadlineAt = null;
        this.matchStartReadyUserOneId = null;
        this.matchStartReadyUserTwoId = null;
    }

    public void moveToMatchStartPending(Instant deadlineAt) {
        this.status = SessionStatus.MATCH_START_PENDING;
        this.matchStartDeadlineAt = deadlineAt;
        this.matchStartReadyUserOneId = null;
        this.matchStartReadyUserTwoId = null;
        this.countdownEndsAt = null;
    }

    public void moveToAwaitingResult() {
        this.status = SessionStatus.AWAITING_RESULT;
    }

    public void moveToFinished() {
        this.status = SessionStatus.FINISHED;
        this.countdownEndsAt = null;
    }

    public boolean registerMatchStartReady(UUID userId) {
        if (userId == null || userId.equals(matchStartReadyUserOneId) || userId.equals(matchStartReadyUserTwoId)) {
            return false;
        }
        if (matchStartReadyUserOneId == null) { matchStartReadyUserOneId = userId; return true; }
        if (matchStartReadyUserTwoId == null) { matchStartReadyUserTwoId = userId; return true; }
        return false;
    }

    @JsonIgnore
    public boolean areBothMatchStartReady() {
        return matchStartReadyUserOneId != null && matchStartReadyUserTwoId != null;
    }

    @JsonIgnore
    public int matchStartReadyCount() {
        int n = 0;
        if (matchStartReadyUserOneId != null) n++;
        if (matchStartReadyUserTwoId != null) n++;
        return n;
    }

    // --- Aggregate query helpers ---

    @JsonIgnore
    public int playerCount() {
        return players.size();
    }

    public Optional<ActivePlayer> findPlayerByUserId(UUID userId) {
        return players.stream().filter(p -> p.getUserId().equals(userId)).findFirst();
    }

    public boolean hasPlayerWithUserId(UUID userId) {
        return players.stream().anyMatch(p -> p.getUserId().equals(userId));
    }

    /** Players sorted by internal ID (stable ordering for home/away determination). */
    public List<ActivePlayer> playersSortedById() {
        return players.stream().sorted(Comparator.comparing(ActivePlayer::getId)).toList();
    }

    public Optional<ActiveRound> findOpenRound() {
        return rounds.stream()
            .filter(r -> r.getStatus() == RoundStatus.OPEN)
            .max(Comparator.comparing(ActiveRound::getStartsAt));
    }

    public Optional<ActiveRound> latestRound() {
        return rounds.stream().max(Comparator.comparing(ActiveRound::getStartsAt));
    }

    public List<ActiveRound> roundsByStatus(RoundStatus status) {
        return rounds.stream()
            .filter(r -> r.getStatus() == status)
            .sorted(Comparator.comparing(ActiveRound::getStartsAt))
            .toList();
    }

    public List<UUID> usedCardIds() {
        return rounds.stream().map(ActiveRound::getCardId).toList();
    }

    @JsonIgnore
    public java.util.Set<UUID> soldCardIds() {
        return rounds.stream()
            .filter(r -> r.getStatus() == RoundStatus.SOLD)
            .map(ActiveRound::getCardId)
            .collect(java.util.stream.Collectors.toSet());
    }

    public Optional<ActiveRound> findRoundById(UUID roundId) {
        return rounds.stream().filter(r -> r.getId().equals(roundId)).findFirst();
    }

    // --- Getters / Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public Instant getCountdownEndsAt() { return countdownEndsAt; }
    public void setCountdownEndsAt(Instant countdownEndsAt) { this.countdownEndsAt = countdownEndsAt; }
    public Long getMatchSeed() { return matchSeed; }
    public void setMatchSeed(Long matchSeed) { this.matchSeed = matchSeed; }
    public Instant getMatchStartDeadlineAt() { return matchStartDeadlineAt; }
    public void setMatchStartDeadlineAt(Instant matchStartDeadlineAt) { this.matchStartDeadlineAt = matchStartDeadlineAt; }
    public UUID getMatchStartReadyUserOneId() { return matchStartReadyUserOneId; }
    public void setMatchStartReadyUserOneId(UUID id) { this.matchStartReadyUserOneId = id; }
    public UUID getMatchStartReadyUserTwoId() { return matchStartReadyUserTwoId; }
    public void setMatchStartReadyUserTwoId(UUID id) { this.matchStartReadyUserTwoId = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }
    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }
    public List<ActivePlayer> getPlayers() { return players; }
    public void setPlayers(List<ActivePlayer> players) { this.players = players; }
    public List<ActiveRound> getRounds() { return rounds; }
    public void setRounds(List<ActiveRound> rounds) { this.rounds = rounds; }
    public List<UUID> getCardPool() { return cardPool; }
    public void setCardPool(List<UUID> cardPool) { this.cardPool = cardPool; }
    public int getCardPoolIndex() { return cardPoolIndex; }
    public void setCardPoolIndex(int cardPoolIndex) { this.cardPoolIndex = cardPoolIndex; }
}
