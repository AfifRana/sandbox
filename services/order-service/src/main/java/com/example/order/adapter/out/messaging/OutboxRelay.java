package com.example.order.adapter.out.messaging;

import com.example.order.application.port.OrderEventOutbox;
import com.example.order.domain.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox: events are appended in the same DB transaction as the
 * aggregate, then a scheduled publisher relays them to Kafka. Guarantees
 * at-least-once delivery without dual-write inconsistency.
 */
@Component
public class OutboxRelay implements OrderEventOutbox {

    private final OutboxEntryRepository repository;
    private final KafkaEventPublisher kafkaPublisher;

    public OutboxRelay(OutboxEntryRepository repository, KafkaEventPublisher kafkaPublisher) {
        this.repository = repository;
        this.kafkaPublisher = kafkaPublisher;
    }

    @Override
    @Transactional
    public void append(Order order) {
        OutboxEntry entry = new OutboxEntry();
        entry.id = UUID.randomUUID();
        entry.aggregateId = order.id();
        entry.type = "order.created";
        entry.payload = toJson(order);
        entry.createdAt = Instant.now();
        entry.published = false;
        repository.save(entry);
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        repository.findTop50ByPublishedFalseOrderByCreatedAtAsc().forEach(entry -> {
            kafkaPublisher.publish(entry.type, entry.aggregateId, entry.payload);
            entry.published = true;
        });
    }

    private String toJson(Order order) {
        return "{\"orderId\":\"%s\",\"customerId\":\"%s\",\"totalAmount\":%s,\"status\":\"%s\"}"
                .formatted(order.id(), order.customerId(), order.totalAmount(), order.status());
    }

    interface OutboxEntryRepository extends JpaRepository<OutboxEntry, UUID> {
        List<OutboxEntry> findTop50ByPublishedFalseOrderByCreatedAtAsc();
    }

    @Entity
    @jakarta.persistence.Table(name = "outbox_events")
    static class OutboxEntry {
        @Id UUID id;
        @Column(name = "aggregate_id", nullable = false) UUID aggregateId;
        @Column(nullable = false, length = 50) String type;
        @Column(nullable = false, length = 4000) String payload;
        @Column(name = "created_at", nullable = false) Instant createdAt;
        @Column(nullable = false) boolean published;
    }
}
