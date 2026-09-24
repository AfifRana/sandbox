package com.example.saga.adapter.in.messaging;

import com.example.saga.application.OrderSagaUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes participant reply events: inventory-events (reserved / rejected /
 * released) and fulfillment-events (shipped / failed), driving the saga
 * state machine. Malformed or unknown messages are logged and skipped.
 */
@Component
public class SagaEventListener {

    private static final Logger log = LoggerFactory.getLogger(SagaEventListener.class);

    private final OrderSagaUseCase useCase;
    private final ObjectMapper objectMapper;

    public SagaEventListener(OrderSagaUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = { "inventory-events", "fulfillment-events" }, groupId = "saga-orchestrator-service")
    public void onEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            String type = event.path("type").asText();
            JsonNode payload = event;
            if (event.has("payload") && event.path("payload").isObject()) {
                // Participant outboxes publish {type, payload} envelopes.
                payload = event.path("payload");
            } else if (type.isEmpty()) {
                // Legacy bare payload: fall back to topic heuristics.
                type = inferTypeFromTopic(record.topic());
            }
            UUID sagaId = UUID.fromString(payload.path("sagaId").asText());
            switch (type) {
                case "inventory.reserved", "inventory.released" -> useCase.onInventoryReserved(sagaId);
                case "inventory.reservation.rejected" ->
                        useCase.onInventoryRejected(sagaId, payload.path("reason").asText(null));
                case "fulfillment.shipped" -> useCase.onFulfillmentShipped(sagaId);
                case "fulfillment.failed" -> useCase.onFulfillmentFailed(sagaId, payload.path("reason").asText(null));
                default -> log.warn("Unknown saga event type={} topic={}", type, record.topic());
            }
        } catch (Exception e) {
            log.warn("Malformed saga event at topic={} partition={} offset={}: {}",
                    record.topic(), record.partition(), record.offset(), record.value());
        }
    }

    /**
     * Legacy fallback for bare payloads published before the {type, payload}
     * envelope was introduced; permissive because a bare inventory reply can
     * only be a reservation result and a bare fulfillment reply only a ship
     * result. Reservation rejections are surfaced via the explicit reason
     * field once envelopes are in place.
     */
    private String inferTypeFromTopic(String topic) {
        return switch (topic) {
            case "inventory-events" -> "inventory.reserved";
            case "fulfillment-events" -> "fulfillment.shipped";
            default -> "";
        };
    }
}