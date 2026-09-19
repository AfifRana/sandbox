package com.example.inventory.adapter.out.messaging;

import com.example.inventory.application.port.InventoryEventOutbox;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox for inventory saga replies: append in the same DB
 * transaction as the reservation/release decision, then relay to Kafka
 * asynchronously. Mirrors order-service's and payment-service's outbox.
 */
@Component
public class InventoryOutboxRelay implements InventoryEventOutbox {

    private final InventoryOutboxEntryRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public InventoryOutboxRelay(InventoryOutboxEntryRepository repository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    @Transactional
    public void appendReserved(UUID sagaId, UUID orderId) {
        append(sagaId, "inventory.reserved", "{\"sagaId\":\"%s\",\"orderId\":\"%s\"}".formatted(sagaId, orderId));
    }

    @Override
    @Transactional
    public void appendRejected(UUID sagaId, UUID orderId, String reason) {
        append(sagaId, "inventory.reservation.rejected",
                "{\"sagaId\":\"%s\",\"orderId\":\"%s\",\"reason\":\"%s\"}"
                        .formatted(sagaId, orderId, escape(reason)));
    }

    @Override
    @Transactional
    public void appendReleased(UUID sagaId, UUID orderId) {
        append(sagaId, "inventory.released", "{\"sagaId\":\"%s\",\"orderId\":\"%s\"}".formatted(sagaId, orderId));
    }

    private void append(UUID sagaId, String type, String payload) {
        InventoryOutboxEntry entry = new InventoryOutboxEntry();
        entry.setId(UUID.randomUUID());
        entry.setAggregateId(sagaId);
        entry.setType(type);
        entry.setPayload(payload);
        entry.setCreatedAt(Instant.now());
        entry.setPublished(false);
        repository.save(entry);
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        repository.findTop50ByPublishedFalseOrderByCreatedAtAsc().forEach(entry -> {
            kafkaTemplate.send("inventory-events", entry.getAggregateId().toString(), entry.getPayload());
            entry.setPublished(true);
        });
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
