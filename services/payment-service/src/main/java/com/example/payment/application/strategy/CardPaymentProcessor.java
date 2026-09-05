package com.example.payment.application.strategy;

import com.example.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Card strategy. Simulates an external PSP call: declines when the amount
 * exceeds a per-method limit (stands in for issuer risk rules).
 */
@Component
public class CardPaymentProcessor implements PaymentProcessor {

    private static final BigDecimal CARD_LIMIT = new BigDecimal("5000.00");

    @Override
    public Payment.PaymentMethod supports() {
        return Payment.PaymentMethod.CARD;
    }

    @Override
    public Payment charge(UUID orderId, UUID customerId, BigDecimal amount) {
        if (amount.compareTo(CARD_LIMIT) > 0) {
            throw new PaymentDeclinedException("Card amount %s exceeds single-charge limit %s"
                    .formatted(amount, CARD_LIMIT));
        }
        return new Payment(UUID.randomUUID(), orderId, customerId, amount,
                Payment.PaymentMethod.CARD, Payment.PaymentStatus.COMPLETED, Instant.now());
    }
}
