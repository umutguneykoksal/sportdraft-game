package com.example.auction.simulation.entity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchResultRepository extends JpaRepository<MatchResult, UUID> {

    Optional<MatchResult> findBySession_Id(UUID sessionId);
}
