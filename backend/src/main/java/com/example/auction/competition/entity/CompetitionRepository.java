package com.example.auction.competition.entity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<CompetitionEntity, UUID> {
    Optional<CompetitionEntity> findBySessionId(UUID sessionId);
}
