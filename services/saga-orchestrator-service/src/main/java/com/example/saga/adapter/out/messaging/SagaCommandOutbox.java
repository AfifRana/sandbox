package com.example.saga.adapter.out.messaging;

import com.example.saga.application.port.SagaCommandPublisher;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox for saga commands: each command the state machine
 * decides is appended in the same DB transaction as the state transition,
 * then relayed to its topic (inventory-commands / fulfillment-commands).
 * Recovery re-issues commands through the same outbox; participants
 * deduplicate by sagaId.
 */
@Component
public class SagaCommandOutbox implements SagaCommandPublisher {

    private final SpringDataSagaOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SagaCommandOutbox(SpringDataSagaOutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    @Transactional
    public void sendInventoryReserve(UUID sagaId, UUID orderId, Map<UUID, Integer> lines) {
        StringBuilder payload = new StringBuilder(
                "{\"type\":\"inventory.reserve.requested\",\"sagaId\":\"%s\",\"orderId\":\"%s\",\"lines\":["
                        .formatted(sagaId, orderId));
        boolean first = true;
        for (Map.Entry<UUID, Integer> line : lines.entrySet()) {
            if (!first) {
                payload.append(',');
            }
            first = false;
            payload.append("{\"productId\":\"%s\",\"quantity\":%d}".formatted(line.getKey(), line.getValue()));
        }
        payload.append("]}");
        append(sagaId, "inventory-commands", "inventory.reserve.requested", payload.toString());
    }

    @Override
    @Transactional
    public void sendInventoryRelease(UUID sagaId, UUID orderId) {
        append(sagaId, "inventory-commands", "inventory.release.requested",
                "{\"type\":\"inventory.release.requested\",\"sagaId\":\"%s\",\"orderId\":\"%s\"}"
                        .formatted(sagaId, orderId));
    }

    @Override
    @Transactional
    public void sendFulfillmentShip(UUID sagaId, UUID orderId, Map<UUID, Integer> lines) {
        StringBuilder payload = new StringBuilder(
                "{\"type\":\"fulfillment.ship.requested\",\"sagaId\":\"%s\",\"orderId\":\"%s\",\"lines\":["
                        .formatted(sagaId, orderId));
        boolean first = true;
        for (Map.Entry<UUID, Integer> line : lines.entrySet()) {
            if (!first) {
                payload.append(',');
            }
            first = false;
            payload.append("{\"productId\":\"%s\",\"quantity\":%d}".formatted(line.getKey(), line.getValue()));
        }
        payload.append("]}");
        append(sagaId, "fulfillment-commands", "fulfillment.ship.requested", payload.toString());
    }

    private void append(UUID sagaId, String topic, String type, String payload) {
        SagaOutboxEntry entry = new SagaOutboxEntry();
        entry.setId(UUID.randomUUID());
        entry.setAggregateId(sagaId);
        entry.setTopic(topic);
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
            kafkaTemplate.send(entry.getTopic(), entry.getAggregateId().toString(), entry.getPayload());
            entry.setPublished(true);
        });
    }
}