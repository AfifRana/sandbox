package com.example.fulfillment.adapter.out.messaging;

import com.example.fulfillment.application.port.FulfillmentEventOutbox;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox for fulfillment saga replies, mirroring
 * inventory-service's InventoryOutboxRelay exactly.
 */
@Component
public class FulfillmentOutboxRelay implements FulfillmentEventOutbox {

    private final SpringDataFulfillmentOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public FulfillmentOutboxRelay(SpringDataFulfillmentOutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    @Transactional
    public void appendShipped(UUID sagaId, UUID orderId) {
        append(sagaId, "fulfillment.shipped", "{\"sagaId\":\"%s\",\"orderId\":\"%s\"}".formatted(sagaId, orderId));
    }

    @Override
    @Transactional
    public void appendFailed(UUID sagaId, UUID orderId, String reason) {
        append(sagaId, "fulfillment.failed",
                "{\"sagaId\":\"%s\",\"orderId\":\"%s\",\"reason\":\"%s\"}"
                        .formatted(sagaId, orderId, escape(reason)));
    }

    private void append(UUID sagaId, String type, String payload) {
        FulfillmentOutboxEntry entry = new FulfillmentOutboxEntry();
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
            // Wrap the payload in an envelope that carries the explicit type
            // discriminator; saga consumers key their dispatch on it. Bare
            // payloads would force fragile topic-based type inference.
            String envelope = "{\"type\":\"%s\",\"payload\":%s}"
                    .formatted(entry.getType(), entry.getPayload());
            kafkaTemplate.send("fulfillment-events", entry.getAggregateId().toString(), envelope);
            entry.setPublished(true);
        });
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}