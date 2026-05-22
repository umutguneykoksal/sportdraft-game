package dev.sportdraft.competition.entity;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompetitionMatchViewRepository extends JpaRepository<CompetitionMatchViewEntity, CompetitionMatchViewId> {

    boolean existsByMatchIdAndUserId(UUID matchId, UUID userId);

    long countByMatchId(UUID matchId);

    @Query("SELECT v.userId FROM CompetitionMatchViewEntity v WHERE v.matchId = :matchId")
    Collection<UUID> findViewerUserIdsByMatchId(@Param("matchId") UUID matchId);
}
