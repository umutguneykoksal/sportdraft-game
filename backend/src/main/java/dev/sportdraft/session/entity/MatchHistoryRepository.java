package dev.sportdraft.session.entity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface MatchHistoryRepository extends JpaRepository<MatchHistory, UUID> {
    Optional<MatchHistory> findBySessionId(UUID sessionId);
    boolean existsBySessionId(UUID sessionId);

    @Modifying
    int deleteByPlayedAtBefore(Instant cutoff);
}
