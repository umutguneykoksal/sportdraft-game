package com.example.auction.common.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    default boolean markIfNew(UUID eventId, String eventType) {
        if (existsById(eventId)) {
            return false;
        }
        save(new ProcessedEvent(eventId, eventType, Instant.now()));
        return true;
    }
}
