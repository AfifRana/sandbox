package com.example.payment.application.strategy;

import com.example.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Bank transfer strategy. Simulates the asynchronous nature of bank
 * transfers: the payment starts PENDING (settles later via reconciliation).
 */
@Component
public class BankTransferPaymentProcessor implements PaymentProcessor {

    @Override
    public Payment.PaymentMethod supports() {
        return Payment.PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public Payment charge(UUID orderId, UUID customerId, BigDecimal amount) {
        // Bank transfers settle asynchronously; the payment remains PENDING
        // until a reconciliation job confirms settlement.
        return new Payment(UUID.randomUUID(), orderId, customerId, amount,
                Payment.PaymentMethod.BANK_TRANSFER, Payment.PaymentStatus.PENDING, Instant.now());
    }
}
