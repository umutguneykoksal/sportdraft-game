package com.example.auction.bidding.entity;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRoundRepository extends JpaRepository<AuctionRound, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AuctionRound r where r.id = :id")
    Optional<AuctionRound> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AuctionRound r where r.session.id = :sessionId and r.status = :status order by r.startsAt desc")
    List<AuctionRound> lockBySessionIdAndStatusOrderByStartsAtDesc(
        @Param("sessionId") UUID sessionId,
        @Param("status") RoundStatus status
    );

    Optional<AuctionRound> findFirstBySession_IdAndStatusOrderByStartsAtDesc(UUID sessionId, RoundStatus status);

    Optional<AuctionRound> findFirstBySession_IdOrderByStartsAtDesc(UUID sessionId);

    List<AuctionRound> findBySession_IdAndStatusOrderByStartsAtAsc(UUID sessionId, RoundStatus status);

    List<AuctionRound> findByStatus(RoundStatus status);

    List<AuctionRound> findByStatusAndEndsAtLessThanEqual(RoundStatus status, Instant now);

    @Query("select r.card.id from AuctionRound r where r.session.id = :sessionId")
    List<UUID> findUsedCardIdsBySessionId(@Param("sessionId") UUID sessionId);
}
