package dev.sportdraft.session.entity;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionPlayerRepository extends JpaRepository<SessionPlayer, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sp from SessionPlayer sp where sp.id = :id")
    Optional<SessionPlayer> lockById(@Param("id") UUID id);

    long countBySession_Id(UUID sessionId);

    boolean existsBySession_IdAndUserId(UUID sessionId, UUID userId);

    Optional<SessionPlayer> findBySession_IdAndUserId(UUID sessionId, UUID userId);

    List<SessionPlayer> findBySession_Id(UUID sessionId);

    List<SessionPlayer> findBySession_IdOrderByIdAsc(UUID sessionId);

    @Query(
        value = "SELECT COUNT(*) FROM session_player_cards WHERE session_player_id = :playerId",
        nativeQuery = true
    )
    long countCardsByPlayerId(@Param("playerId") UUID playerId);

    @Query(
        value = """
            SELECT COUNT(*) FROM session_player_cards spc
            INNER JOIN card c ON c.id = spc.card_id
            WHERE spc.session_player_id = :playerId AND c.position = :position
            """,
        nativeQuery = true
    )
    long countCardsByPlayerIdAndPosition(@Param("playerId") UUID playerId, @Param("position") String position);
}
