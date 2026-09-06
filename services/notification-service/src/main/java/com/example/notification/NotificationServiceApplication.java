package com.example.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

@Component
class OrderEventNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderEventNotifier.class);

    private final NotificationDeduplicator deduplicator;
    private final ObjectMapper objectMapper;

    OrderEventNotifier(NotificationDeduplicator deduplicator, ObjectMapper objectMapper) {
        this.deduplicator = deduplicator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            String orderId = event.path("orderId").asText();
            String status = event.path("status").asText();
            if (deduplicator.firstDelivery(orderId + ":" + status)) {
                log.info("Notifying customer about order {} ({})", orderId, status);
            } else {
                log.debug("Duplicate order event suppressed: {} ({})", orderId, status);
            }
        } catch (Exception e) {
            log.warn("Malformed order event at partition={} offset={}: {}",
                    record.partition(), record.offset(), record.value());
        }
    }
}
