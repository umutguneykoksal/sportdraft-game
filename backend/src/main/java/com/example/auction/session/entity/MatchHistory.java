package com.example.auction.session.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "match_history")
public class MatchHistory {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "home_user_id", nullable = false)
    private UUID homeUserId;

    @Column(name = "away_user_id", nullable = false)
    private UUID awayUserId;

    @Column(name = "home_score", nullable = false)
    private int homeScore;

    @Column(name = "away_score", nullable = false)
    private int awayScore;

    @Column(name = "home_formation", nullable = false, length = 30)
    private String homeFormation;

    @Column(name = "away_formation", nullable = false, length = 30)
    private String awayFormation;

    @Column(nullable = false)
    private long seed;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "replay_data", nullable = false, columnDefinition = "jsonb")
    private String replayData;

    protected MatchHistory() {
    }

    public MatchHistory(UUID id, UUID sessionId, UUID homeUserId, UUID awayUserId,
                        int homeScore, int awayScore,
                        String homeFormation, String awayFormation,
                        long seed, Instant playedAt, String replayData) {
        this.id = id;
        this.sessionId = sessionId;
        this.homeUserId = homeUserId;
        this.awayUserId = awayUserId;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.homeFormation = homeFormation;
        this.awayFormation = awayFormation;
        this.seed = seed;
        this.playedAt = playedAt;
        this.replayData = replayData;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getHomeUserId() { return homeUserId; }
    public UUID getAwayUserId() { return awayUserId; }
    public int getHomeScore() { return homeScore; }
    public int getAwayScore() { return awayScore; }
    public String getHomeFormation() { return homeFormation; }
    public String getAwayFormation() { return awayFormation; }
    public long getSeed() { return seed; }
    public Instant getPlayedAt() { return playedAt; }
    public String getReplayData() { return replayData; }
}
