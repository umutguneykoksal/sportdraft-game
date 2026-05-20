package com.example.auction.competition.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "competition_match")
public class CompetitionMatchEntity {

    @Id
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

    @Column(name = "round_key", nullable = false)
    private String roundKey;

    @Column(name = "home_user_id", nullable = false)
    private UUID homeUserId;

    @Column(name = "away_user_id", nullable = false)
    private UUID awayUserId;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitionMatchStatus status;

    @Column(nullable = false)
    private long seed;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "winner_user_id")
    private UUID winnerUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "replay_data", columnDefinition = "jsonb")
    private String replayDataJson;

    @Column(name = "home_penalty_score")
    private Integer homePenaltyScore;

    @Column(name = "away_penalty_score")
    private Integer awayPenaltyScore;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCompetitionId() { return competitionId; }
    public void setCompetitionId(UUID competitionId) { this.competitionId = competitionId; }
    public String getRoundKey() { return roundKey; }
    public void setRoundKey(String roundKey) { this.roundKey = roundKey; }
    public UUID getHomeUserId() { return homeUserId; }
    public void setHomeUserId(UUID homeUserId) { this.homeUserId = homeUserId; }
    public UUID getAwayUserId() { return awayUserId; }
    public void setAwayUserId(UUID awayUserId) { this.awayUserId = awayUserId; }
    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }
    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }
    public CompetitionMatchStatus getStatus() { return status; }
    public void setStatus(CompetitionMatchStatus status) { this.status = status; }
    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public UUID getWinnerUserId() { return winnerUserId; }
    public void setWinnerUserId(UUID winnerUserId) { this.winnerUserId = winnerUserId; }
    public String getReplayDataJson() { return replayDataJson; }
    public void setReplayDataJson(String replayDataJson) { this.replayDataJson = replayDataJson; }
    public Integer getHomePenaltyScore() { return homePenaltyScore; }
    public void setHomePenaltyScore(Integer homePenaltyScore) { this.homePenaltyScore = homePenaltyScore; }
    public Integer getAwayPenaltyScore() { return awayPenaltyScore; }
    public void setAwayPenaltyScore(Integer awayPenaltyScore) { this.awayPenaltyScore = awayPenaltyScore; }
}
