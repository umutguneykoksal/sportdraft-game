package com.example.auction.session.entity;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {

    List<GameSession> findByStatusOrderByCreatedAtAsc(SessionStatus status);

    List<GameSession> findByStatusAndCountdownEndsAtLessThanEqual(SessionStatus status, Instant now);

    List<GameSession> findByStatusAndMatchStartDeadlineAtLessThanEqual(SessionStatus status, Instant now);

    List<GameSession> findByStatusNot(SessionStatus status);

    Optional<GameSession> findByIdAndStatus(UUID id, SessionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GameSession s where s.id = :id")
    Optional<GameSession> lockById(@Param("id") UUID id);
}
