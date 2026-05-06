package com.example.auction.session.entity;

import com.example.auction.bidding.entity.AuctionRound;
import com.example.auction.simulation.entity.MatchResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "game_session")
public class GameSession {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SessionStatus status;

    @Column(name = "countdown_ends_at")
    private Instant countdownEndsAt;

    @Column(name = "match_seed")
    private Long matchSeed;

    @Column(name = "match_start_deadline_at")
    private Instant matchStartDeadlineAt;

    @Column(name = "match_start_ready_user_one_id")
    private UUID matchStartReadyUserOneId;

    @Column(name = "match_start_ready_user_two_id")
    private UUID matchStartReadyUserTwoId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY)
    private List<SessionPlayer> players = new ArrayList<>();

    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY)
    private List<AuctionRound> rounds = new ArrayList<>();

    @OneToOne(mappedBy = "session", fetch = FetchType.LAZY)
    private MatchResult matchResult;

    protected GameSession() {
    }

    public GameSession(UUID id, SessionStatus status, Instant createdAt) {
        this.id = id;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getCountdownEndsAt() {
        return countdownEndsAt;
    }

    public Long getMatchSeed() {
        return matchSeed;
    }

    public Instant getMatchStartDeadlineAt() {
        return matchStartDeadlineAt;
    }

    public UUID getMatchStartReadyUserOneId() {
        return matchStartReadyUserOneId;
    }

    public UUID getMatchStartReadyUserTwoId() {
        return matchStartReadyUserTwoId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<SessionPlayer> getPlayers() {
        return players;
    }

    public List<AuctionRound> getRounds() {
        return rounds;
    }

    public MatchResult getMatchResult() {
        return matchResult;
    }

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

    public boolean registerMatchStartReady(UUID userId) {
        if (userId == null || userId.equals(matchStartReadyUserOneId) || userId.equals(matchStartReadyUserTwoId)) {
            return false;
        }
        if (matchStartReadyUserOneId == null) {
            matchStartReadyUserOneId = userId;
            return true;
        }
        if (matchStartReadyUserTwoId == null) {
            matchStartReadyUserTwoId = userId;
            return true;
        }
        return false;
    }

    public boolean areBothMatchStartReady() {
        return matchStartReadyUserOneId != null && matchStartReadyUserTwoId != null;
    }

    public int matchStartReadyCount() {
        int n = 0;
        if (matchStartReadyUserOneId != null) {
            n++;
        }
        if (matchStartReadyUserTwoId != null) {
            n++;
        }
        return n;
    }

    public void moveToAwaitingResult() {
        this.status = SessionStatus.AWAITING_RESULT;
    }

    public void moveToFinished() {
        this.status = SessionStatus.FINISHED;
        this.countdownEndsAt = null;
    }
}
