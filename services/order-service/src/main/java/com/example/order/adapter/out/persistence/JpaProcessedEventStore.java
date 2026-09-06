package com.example.order.adapter.out.persistence;

import com.example.order.application.port.ProcessedEventStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class JpaProcessedEventStore implements ProcessedEventStore {

    private final ProcessedEventRepository repository;

    public JpaProcessedEventStore(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /**
     * First claim inserts the inbox row; a replay hits the composite PK and is
     * treated as "already processed". Both paths run inside the caller's
     * transaction, so a rollback also rolls back the claim.
     */
    @Override
    public boolean claim(UUID eventId, String consumerGroup) {
        if (repository.existsById(new ProcessedEventId(eventId, consumerGroup))) {
            return false;
        }
        ProcessedEventEntity entry = new ProcessedEventEntity();
        entry.eventId = eventId;
        entry.consumerGroup = consumerGroup;
        entry.processedAt = Instant.now();
        try {
            repository.saveAndFlush(entry);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false; // concurrent duplicate — another instance claimed it first
        }
    }
}
