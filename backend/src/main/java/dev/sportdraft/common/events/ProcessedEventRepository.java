package dev.sportdraft.common.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO processed_event(event_id, event_type, processed_at)
            VALUES (:eventId, :eventType, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("eventId") UUID eventId,
        @Param("eventType") String eventType,
        @Param("processedAt") Instant processedAt
    );
}
