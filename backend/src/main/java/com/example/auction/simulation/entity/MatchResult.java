package com.example.auction.simulation.entity;

import com.example.auction.session.entity.GameSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_result")
public class MatchResult {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private GameSession session;

    @Column(name = "home_score", nullable = false)
    private int homeScore;

    @Column(name = "away_score", nullable = false)
    private int awayScore;

    @Column(name = "seed", nullable = false)
    private long seed;

    @Column(name = "simulated_at", nullable = false)
    private Instant simulatedAt;

    protected MatchResult() {
    }

    public MatchResult(UUID id, GameSession session, int homeScore, int awayScore, long seed, Instant simulatedAt) {
        this.id = id;
        this.session = session;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.seed = seed;
        this.simulatedAt = simulatedAt;
    }

    public UUID getId() {
        return id;
    }

    public GameSession getSession() {
        return session;
    }

    public int getHomeScore() {
        return homeScore;
    }

    public int getAwayScore() {
        return awayScore;
    }

    public long getSeed() {
        return seed;
    }

    public Instant getSimulatedAt() {
        return simulatedAt;
    }
}
