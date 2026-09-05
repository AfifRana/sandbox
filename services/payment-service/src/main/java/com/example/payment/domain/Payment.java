package com.example.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        Instant createdAt
) {
    public enum PaymentMethod { CARD, WALLET, BANK_TRANSFER }

    public enum PaymentStatus { PENDING, COMPLETED, FAILED }
}
