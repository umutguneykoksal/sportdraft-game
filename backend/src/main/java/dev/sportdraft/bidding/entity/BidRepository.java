package dev.sportdraft.bidding.entity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    boolean existsByRoundIdAndIdempotencyKey(UUID roundId, UUID idempotencyKey);
}
