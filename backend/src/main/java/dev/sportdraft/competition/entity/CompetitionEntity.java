package dev.sportdraft.competition.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "competition")
public class CompetitionEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitionStatus status;

    @Column(name = "champion_user_id")
    private UUID championUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_snapshot", columnDefinition = "jsonb")
    private String draftSnapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "tournament_phase", nullable = false)
    private TournamentPhase tournamentPhase = TournamentPhase.SEMIS;

    @Column(name = "intermission_ends_at")
    private Instant intermissionEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "league_phase")
    private LeaguePhase leaguePhase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "co_champion_user_ids", columnDefinition = "jsonb")
    private String coChampionUserIdsJson;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public CompetitionType getType() { return type; }
    public void setType(CompetitionType type) { this.type = type; }
    public CompetitionStatus getStatus() { return status; }
    public void setStatus(CompetitionStatus status) { this.status = status; }
    public UUID getChampionUserId() { return championUserId; }
    public void setChampionUserId(UUID championUserId) { this.championUserId = championUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getDraftSnapshotJson() { return draftSnapshotJson; }
    public void setDraftSnapshotJson(String draftSnapshotJson) { this.draftSnapshotJson = draftSnapshotJson; }
    public TournamentPhase getTournamentPhase() { return TournamentPhase.effective(tournamentPhase); }
    public void setTournamentPhase(TournamentPhase tournamentPhase) { this.tournamentPhase = tournamentPhase; }
    public Instant getIntermissionEndsAt() { return intermissionEndsAt; }
    public void setIntermissionEndsAt(Instant intermissionEndsAt) { this.intermissionEndsAt = intermissionEndsAt; }
    public LeaguePhase getLeaguePhase() { return LeaguePhase.effective(leaguePhase); }
    public void setLeaguePhase(LeaguePhase leaguePhase) { this.leaguePhase = leaguePhase; }
    public String getCoChampionUserIdsJson() { return coChampionUserIdsJson; }
    public void setCoChampionUserIdsJson(String coChampionUserIdsJson) { this.coChampionUserIdsJson = coChampionUserIdsJson; }
}
