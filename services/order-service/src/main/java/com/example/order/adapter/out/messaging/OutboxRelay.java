package com.example.order.adapter.out.messaging;

import com.example.order.application.port.OrderEventOutbox;
import com.example.order.domain.Order;
import java.time.Instant;
import java.util.UUID;
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
        entry.setId(UUID.randomUUID());
        entry.setAggregateId(order.id());
        entry.setType("order.created");
        entry.setPayload(toJson(order));
        entry.setCreatedAt(Instant.now());
        entry.setPublished(false);
        repository.save(entry);
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        repository.findTop50ByPublishedFalseOrderByCreatedAtAsc().forEach(entry -> {
            kafkaPublisher.publish(entry.getType(), entry.getAggregateId(), entry.getPayload());
            entry.setPublished(true);
        });
    }

    private String toJson(Order order) {
        return "{\"orderId\":\"%s\",\"customerId\":\"%s\",\"totalAmount\":%s,\"status\":\"%s\"}"
                .formatted(order.id(), order.customerId(), order.totalAmount(), order.status());
    }
}
