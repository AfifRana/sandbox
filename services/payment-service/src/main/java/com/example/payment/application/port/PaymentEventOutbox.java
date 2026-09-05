package com.example.payment.application.port;

import com.example.payment.domain.Payment;

/**
 * Outbox port: payment events are appended in the same DB transaction as the
 * payment itself, then relayed to Kafka asynchronously (transactional outbox).
 */
public interface PaymentEventOutbox {
    void append(Payment payment);
}
