package com.example.order.adapter.in.messaging;

import com.example.order.application.HandlePaymentPaidEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes payment.paid events published by payment-service. Delivery is
 * at-least-once; the use case's transactional inbox guarantees exactly-once
 * processing (dedupe by paymentId, not just the order status check).
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final HandlePaymentPaidEvent handlePaymentPaid;
    private final ObjectMapper objectMapper;

    public PaymentEventListener(HandlePaymentPaidEvent handlePaymentPaid, ObjectMapper objectMapper) {
        this.handlePaymentPaid = handlePaymentPaid;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service")
    public void onPaymentPaid(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            UUID paymentId = UUID.fromString(event.path("paymentId").asText());
            UUID orderId = UUID.fromString(event.path("orderId").asText());
            handlePaymentPaid.handle(paymentId, orderId);
        } catch (Exception e) {
            log.warn("Malformed payment.paid event at partition={} offset={}: {}",
                    record.partition(), record.offset(), record.value());
            // skip — dead-letter in a real system
        }
    }
}
