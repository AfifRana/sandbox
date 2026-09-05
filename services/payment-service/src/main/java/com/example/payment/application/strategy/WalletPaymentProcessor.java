package com.example.payment.application.strategy;

import com.example.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wallet strategy. Simulates a wallet balance check: declines when the
 * amount exceeds the (fixed demo) wallet balance.
 */
@Component
public class WalletPaymentProcessor implements PaymentProcessor {

    private static final BigDecimal WALLET_BALANCE = new BigDecimal("1000.00");

    @Override
    public Payment.PaymentMethod supports() {
        return Payment.PaymentMethod.WALLET;
    }

    @Override
    public Payment charge(UUID orderId, UUID customerId, BigDecimal amount) {
        if (amount.compareTo(WALLET_BALANCE) > 0) {
            throw new PaymentDeclinedException("Wallet balance %s insufficient for amount %s"
                    .formatted(WALLET_BALANCE, amount));
        }
        return new Payment(UUID.randomUUID(), orderId, customerId, amount,
                Payment.PaymentMethod.WALLET, Payment.PaymentStatus.COMPLETED, Instant.now());
    }
}
