package com.example.auction.session.entity;

import com.example.auction.bidding.entity.Position;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionPlayerRepository extends JpaRepository<SessionPlayer, UUID> {

    long countBySession_Id(UUID sessionId);

    boolean existsBySession_IdAndUserId(UUID sessionId, UUID userId);

    Optional<SessionPlayer> findBySession_IdAndUserId(UUID sessionId, UUID userId);

    List<SessionPlayer> findBySession_Id(UUID sessionId);

    @Query("select count(c) from SessionPlayer sp join sp.cards c where sp.id = :playerId")
    long countCardsByPlayerId(@Param("playerId") UUID playerId);

    @Query("select count(c) from SessionPlayer sp join sp.cards c where sp.id = :playerId and c.position = :position")
    long countCardsByPlayerIdAndPosition(@Param("playerId") UUID playerId, @Param("position") Position position);
}
