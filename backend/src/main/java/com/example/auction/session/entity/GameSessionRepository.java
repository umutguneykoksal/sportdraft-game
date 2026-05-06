package com.example.auction.session.entity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {

    List<GameSession> findByStatusOrderByCreatedAtAsc(SessionStatus status);

    List<GameSession> findByStatusAndCountdownEndsAtLessThanEqual(SessionStatus status, Instant now);

    List<GameSession> findByStatusNot(SessionStatus status);

    Optional<GameSession> findByIdAndStatus(UUID id, SessionStatus status);
}
