package com.example.order.adapter.in.messaging;

import com.example.order.application.MarkOrderPaidUseCase;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes payment.paid events published by payment-service. Delivery is
 * at-least-once, so the use case's status check makes handling idempotent.
 */
@Component
public class PaymentEventListener {

    private final MarkOrderPaidUseCase markOrderPaid;

    public PaymentEventListener(MarkOrderPaidUseCase markOrderPaid) {
        this.markOrderPaid = markOrderPaid;
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service")
    public void onPaymentPaid(ConsumerRecord<String, String> record) {
        UUID orderId = extractOrderId(record.value());
        if (orderId != null) {
            markOrderPaid.markPaid(orderId);
        }
    }

    private UUID extractOrderId(String payload) {
        try {
            int start = payload.indexOf("\"orderId\":\"") + "\"orderId\":\"".length();
            int end = payload.indexOf('"', start);
            return UUID.fromString(payload.substring(start, end));
        } catch (Exception e) {
            return null; // malformed payload — skip (dead-letter in a real system)
        }
    }
}
