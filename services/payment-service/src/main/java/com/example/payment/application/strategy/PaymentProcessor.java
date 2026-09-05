package com.example.payment.application.strategy;

import com.example.payment.domain.Payment;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Strategy interface for payment processing. Each payment method implements
 * its own charging logic; the use case dispatches by {@link Payment.PaymentMethod}.
 *
 * Adding a new method (e.g. QRIS, BNPL) means adding a new implementation —
 * no modification to existing code (Open/Closed Principle).
 */
public interface PaymentProcessor {

    /** The method this strategy handles. */
    Payment.PaymentMethod supports();

    /**
     * Charge the amount via this method.
     *
     * @throws PaymentDeclinedException if the charge is declined
     */
    Payment charge(UUID orderId, UUID customerId, BigDecimal amount);
}
