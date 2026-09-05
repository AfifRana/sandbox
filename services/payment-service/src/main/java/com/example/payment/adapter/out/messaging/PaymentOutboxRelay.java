package com.example.payment.adapter.out.messaging;

import com.example.payment.application.port.PaymentEventOutbox;
import com.example.payment.domain.Payment;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox for payment events. Mirrors order-service's relay:
 * append in the payment's DB transaction, publish to Kafka asynchronously.
 */
@Component
public class PaymentOutboxRelay implements PaymentEventOutbox {

    private final PaymentOutboxEntryRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentOutboxRelay(PaymentOutboxEntryRepository repository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    @Transactional
    public void append(Payment payment) {
        PaymentOutboxEntry entry = new PaymentOutboxEntry();
        entry.setId(UUID.randomUUID());
        entry.setAggregateId(payment.orderId());
        entry.setType("payment.paid");
        entry.setPayload(toJson(payment));
        entry.setCreatedAt(Instant.now());
        entry.setPublished(false);
        repository.save(entry);
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        repository.findTop50ByPublishedFalseOrderByCreatedAtAsc().forEach(entry -> {
            kafkaTemplate.send("payment-events", entry.getAggregateId().toString(), entry.getPayload());
            entry.setPublished(true);
        });
    }

    private String toJson(Payment payment) {
        return "{\"paymentId\":\"%s\",\"orderId\":\"%s\",\"customerId\":\"%s\",\"amount\":%s,\"method\":\"%s\",\"status\":\"%s\"}"
                .formatted(payment.id(), payment.orderId(), payment.customerId(),
                        payment.amount(), payment.method(), payment.status());
    }
}
