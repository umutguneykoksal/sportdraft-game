package dev.sportdraft.competition.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<CompetitionEntity, UUID> {
    Optional<CompetitionEntity> findBySessionId(UUID sessionId);

    List<CompetitionEntity> findByStatus(CompetitionStatus status);
}
