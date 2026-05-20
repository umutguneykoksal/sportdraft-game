package com.example.auction.session.redis;

import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.simulation.entity.MatchEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root for an active game session stored in Redis.
 * Combines what was previously GameSession + SessionPlayer + AuctionRound + Bid
 * into a single document for atomic reads/writes.
 */
public class ActiveSession {

    private UUID id;
    private GameMode gameMode = GameMode.DUEL;
    private int maxPlayers = 2;
    private SessionStatus status;
    private Instant countdownEndsAt;
    private Long matchSeed;
    private Instant matchStartDeadlineAt;
    private UUID matchStartReadyUserOneId;
    private UUID matchStartReadyUserTwoId;
    private Set<UUID> matchStartReadyUserIds = new LinkedHashSet<>();
    private List<UUID> turnOrder = new ArrayList<>();
    private Instant createdAt;
    private long version;
    private Integer homeScore;
    private Integer awayScore;
    private List<ActivePlayer> players = new ArrayList<>();
    private List<ActiveRound> rounds = new ArrayList<>();
    private List<UUID> cardPool = new ArrayList<>();
    private int cardPoolIndex;
    private List<MatchEvent> matchTimeline = new ArrayList<>();

    public ActiveSession() {
    }

    public ActiveSession(UUID id, SessionStatus status, Instant createdAt) {
        this(id, status, createdAt, GameMode.DUEL);
    }

    public ActiveSession(UUID id, SessionStatus status, Instant createdAt, GameMode gameMode) {
        this.id = id;
        this.status = status;
        this.createdAt = createdAt;
        this.version = 0;
        this.gameMode = gameMode != null ? gameMode : GameMode.DUEL;
        this.maxPlayers = this.gameMode.maxPlayers();
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
        clearMatchStartReady();
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
        clearMatchStartReady();
    }

    public void moveToMatchStartPending(Instant deadlineAt) {
        this.status = SessionStatus.MATCH_START_PENDING;
        this.matchStartDeadlineAt = deadlineAt;
        clearMatchStartReady();
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
        syncReadyIdsFromLegacyFields();
        if (userId == null || matchStartReadyUserIds.contains(userId)) {
            return false;
        }
        matchStartReadyUserIds.add(userId);
        syncLegacyReadyFieldsFromSet();
        return true;
    }

    @JsonIgnore
    public boolean areAllMatchStartReady() {
        syncReadyIdsFromLegacyFields();
        return matchStartReadyUserIds.size() >= maxPlayers;
    }

    /** @deprecated use {@link #areAllMatchStartReady()} */
    @JsonIgnore
    public boolean areBothMatchStartReady() {
        return areAllMatchStartReady();
    }

    @JsonIgnore
    public int matchStartReadyCount() {
        syncReadyIdsFromLegacyFields();
        return matchStartReadyUserIds.size();
    }

    public void addToTurnOrder(UUID userId) {
        if (userId != null && !turnOrder.contains(userId)) {
            turnOrder.add(userId);
        }
    }

    @JsonIgnore
    public void ensureTurnOrder() {
        if (turnOrder.isEmpty()) {
            players.forEach(p -> turnOrder.add(p.getUserId()));
        }
    }

    private void clearMatchStartReady() {
        matchStartReadyUserOneId = null;
        matchStartReadyUserTwoId = null;
        matchStartReadyUserIds.clear();
    }

    @JsonIgnore
    public boolean isMatchStartReady(UUID userId) {
        syncReadyIdsFromLegacyFields();
        return userId != null && matchStartReadyUserIds.contains(userId);
    }

    void syncReadyIdsFromLegacyFields() {
        if (matchStartReadyUserIds == null) {
            matchStartReadyUserIds = new LinkedHashSet<>();
        }
        if (matchStartReadyUserOneId != null) {
            matchStartReadyUserIds.add(matchStartReadyUserOneId);
        }
        if (matchStartReadyUserTwoId != null) {
            matchStartReadyUserIds.add(matchStartReadyUserTwoId);
        }
    }

    private void syncLegacyReadyFieldsFromSet() {
        List<UUID> ids = new ArrayList<>(matchStartReadyUserIds);
        matchStartReadyUserOneId = ids.size() > 0 ? ids.get(0) : null;
        matchStartReadyUserTwoId = ids.size() > 1 ? ids.get(1) : null;
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
    public GameMode getGameMode() { return gameMode != null ? gameMode : GameMode.DUEL; }
    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode != null ? gameMode : GameMode.DUEL;
        this.maxPlayers = this.gameMode.maxPlayers();
    }
    public int getMaxPlayers() { return maxPlayers > 0 ? maxPlayers : getGameMode().maxPlayers(); }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
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
    public List<MatchEvent> getMatchTimeline() { return matchTimeline; }
    public void setMatchTimeline(List<MatchEvent> matchTimeline) { this.matchTimeline = matchTimeline; }
    public Set<UUID> getMatchStartReadyUserIds() { return matchStartReadyUserIds; }
    public void setMatchStartReadyUserIds(Set<UUID> matchStartReadyUserIds) {
        this.matchStartReadyUserIds = matchStartReadyUserIds != null
            ? new LinkedHashSet<>(matchStartReadyUserIds) : new LinkedHashSet<>();
    }
    public List<UUID> getTurnOrder() { return turnOrder; }
    public void setTurnOrder(List<UUID> turnOrder) {
        this.turnOrder = turnOrder != null ? new ArrayList<>(turnOrder) : new ArrayList<>();
    }
}
