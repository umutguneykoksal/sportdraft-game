package com.example.auction.session.entity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchHistoryRepository extends JpaRepository<MatchHistory, UUID> {
    Optional<MatchHistory> findBySessionId(UUID sessionId);
    boolean existsBySessionId(UUID sessionId);
}
