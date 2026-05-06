package com.example.auction.bidding.entity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRoundRepository extends JpaRepository<AuctionRound, UUID> {

    Optional<AuctionRound> findFirstBySession_IdAndStatusOrderByStartsAtDesc(UUID sessionId, RoundStatus status);

    Optional<AuctionRound> findFirstBySession_IdOrderByStartsAtDesc(UUID sessionId);

    List<AuctionRound> findByStatusAndEndsAtLessThanEqual(RoundStatus status, Instant now);
}
