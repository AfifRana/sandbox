package com.example.payment.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payment.domain.Payment;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentProcessorStrategyTest {

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @Test
    void cardSupportsCardAndAcceptsLimit() {
        PaymentProcessor processor = new CardPaymentProcessor();

        assertThat(processor.supports()).isEqualTo(Payment.PaymentMethod.CARD);
        assertThat(processor.charge(orderId, customerId, new BigDecimal("5000.00")))
                .extracting(Payment::status)
                .isEqualTo(Payment.PaymentStatus.COMPLETED);
    }

    @Test
    void walletSupportsWalletAndAcceptsBalance() {
        PaymentProcessor processor = new WalletPaymentProcessor();

        assertThat(processor.supports()).isEqualTo(Payment.PaymentMethod.WALLET);
        assertThat(processor.charge(orderId, customerId, new BigDecimal("1000.00")))
                .extracting(Payment::status)
                .isEqualTo(Payment.PaymentStatus.COMPLETED);
    }

    @Test
    void walletOverBalanceIsDeclined() {
        PaymentProcessor processor = new WalletPaymentProcessor();

        assertThatThrownBy(() -> processor.charge(orderId, customerId,
                new BigDecimal("1000.01")))
                .isInstanceOf(PaymentDeclinedException.class);
    }

    @Test
    void bankTransferSupportsBankTransferAndStartsPending() {
        PaymentProcessor processor = new BankTransferPaymentProcessor();

        assertThat(processor.supports()).isEqualTo(Payment.PaymentMethod.BANK_TRANSFER);
        assertThat(processor.charge(orderId, customerId, BigDecimal.TEN))
                .extracting(Payment::status)
                .isEqualTo(Payment.PaymentStatus.PENDING);
    }
}
