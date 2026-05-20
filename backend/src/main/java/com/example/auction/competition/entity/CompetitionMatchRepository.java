package com.example.auction.competition.entity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionMatchRepository extends JpaRepository<CompetitionMatchEntity, UUID> {
    List<CompetitionMatchEntity> findByCompetitionIdOrderBySortOrderAsc(UUID competitionId);
}
