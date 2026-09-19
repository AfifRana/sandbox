package com.example.inventory.adapter.in.messaging;

import com.example.inventory.application.ReleaseStockUseCase;
import com.example.inventory.application.ReserveStockUseCase;
import com.example.inventory.domain.ReservationLine;
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
 * Consumes saga commands from the orchestrator. Both command kinds share one
 * topic, so each payload carries an explicit "type" discriminator.
 */
@Component
public class InventoryCommandListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);

    private final ReserveStockUseCase reserveStock;
    private final ReleaseStockUseCase releaseStock;
    private final ObjectMapper objectMapper;

    public InventoryCommandListener(ReserveStockUseCase reserveStock, ReleaseStockUseCase releaseStock,
            ObjectMapper objectMapper) {
        this.reserveStock = reserveStock;
        this.releaseStock = releaseStock;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory-commands", groupId = "inventory-service")
    public void onCommand(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            String type = event.path("type").asText();
            UUID sagaId = UUID.fromString(event.path("sagaId").asText());
            UUID orderId = UUID.fromString(event.path("orderId").asText());
            switch (type) {
                case "inventory.reserve.requested" -> reserveStock.reserve(sagaId, orderId, parseLines(event));
                case "inventory.release.requested" -> releaseStock.release(sagaId, orderId);
                default -> log.warn("Unknown inventory command type={}", type);
            }
        } catch (Exception e) {
            log.warn("Malformed inventory command at partition={} offset={}: {}",
                    record.partition(), record.offset(), record.value());
            // skip — dead-letter in a real system
        }
    }

    private List<ReservationLine> parseLines(JsonNode event) {
        List<ReservationLine> lines = new ArrayList<>();
        event.path("lines").forEach(line ->
                lines.add(new ReservationLine(UUID.fromString(line.path("productId").asText()),
                        line.path("quantity").asInt())));
        return lines;
    }
}
