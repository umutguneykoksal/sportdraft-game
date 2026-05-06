package com.example.auction.session.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionPlayerRepository extends JpaRepository<SessionPlayer, UUID> {

    long countBySession_Id(UUID sessionId);

    boolean existsBySession_IdAndUserId(UUID sessionId, UUID userId);

    Optional<SessionPlayer> findBySession_IdAndUserId(UUID sessionId, UUID userId);

    List<SessionPlayer> findBySession_Id(UUID sessionId);
}
