package dev.sportdraft.bidding.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CardRepository extends JpaRepository<Card, UUID> {

    @Query(value = "select * from card order by random() limit 1", nativeQuery = true)
    Optional<Card> findRandomCard();

    List<Card> findByPosition(Position position);
}
