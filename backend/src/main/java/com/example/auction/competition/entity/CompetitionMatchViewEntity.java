package com.example.auction.competition.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "competition_match_view")
@IdClass(CompetitionMatchViewId.class)
public class CompetitionMatchViewEntity {

    @Id
    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt = Instant.now();

    public UUID getMatchId() { return matchId; }
    public void setMatchId(UUID matchId) { this.matchId = matchId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Instant getWatchedAt() { return watchedAt; }
    public void setWatchedAt(Instant watchedAt) { this.watchedAt = watchedAt; }
}
