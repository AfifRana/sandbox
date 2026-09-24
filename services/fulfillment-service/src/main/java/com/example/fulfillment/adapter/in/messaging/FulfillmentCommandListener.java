package com.example.fulfillment.adapter.in.messaging;

import com.example.fulfillment.application.ShipOrderUseCase;
import com.example.fulfillment.domain.ShipmentLine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes ship commands from the orchestrator. Mirrors inventory-service's
 * command listener: type-discriminated payload, malformed messages skipped
 * (dead-letter in a real system).
 */
@Component
public class FulfillmentCommandListener {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentCommandListener.class);

    private final ShipOrderUseCase shipOrder;
    private final ObjectMapper objectMapper;

    public FulfillmentCommandListener(ShipOrderUseCase shipOrder, ObjectMapper objectMapper) {
        this.shipOrder = shipOrder;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "fulfillment-commands", groupId = "fulfillment-service")
    public void onCommand(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            String type = event.path("type").asText();
            UUID sagaId = UUID.fromString(event.path("sagaId").asText());
            UUID orderId = UUID.fromString(event.path("orderId").asText());
            switch (type) {
                case "fulfillment.ship.requested" -> shipOrder.ship(sagaId, orderId, parseLines(event));
                default -> log.warn("Unknown fulfillment command type={}", type);
            }
        } catch (Exception e) {
            log.warn("Malformed fulfillment command at partition={} offset={}: {}",
                    record.partition(), record.offset(), record.value());
        }
    }

    private List<ShipmentLine> parseLines(JsonNode event) {
        List<ShipmentLine> lines = new ArrayList<>();
        event.path("lines").forEach(line ->
                lines.add(new ShipmentLine(UUID.fromString(line.path("productId").asText()),
                        line.path("quantity").asInt())));
        return lines;
    }
}