package com.example.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payment.application.port.PaymentEventOutbox;
import com.example.payment.application.port.PaymentRepository;
import com.example.payment.application.strategy.BankTransferPaymentProcessor;
import com.example.payment.application.strategy.CardPaymentProcessor;
import com.example.payment.application.strategy.PaymentDeclinedException;
import com.example.payment.application.strategy.WalletPaymentProcessor;
import com.example.payment.domain.Payment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProcessPaymentUseCaseTest {

    @Mock
    PaymentRepository repository;

    @Mock
    PaymentEventOutbox outbox;

    ProcessPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcessPaymentUseCase(List.of(
                new CardPaymentProcessor(),
                new WalletPaymentProcessor(),
                new BankTransferPaymentProcessor()), repository, outbox);
        lenient().when(repository.save(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void dispatchesToCardStrategyAndEmitsPaymentPaid() {
        UUID orderId = UUID.randomUUID();

        Payment result = useCase.process(orderId, UUID.randomUUID(),
                new BigDecimal("99.99"), Payment.PaymentMethod.CARD);

        assertThat(result.status()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(result.method()).isEqualTo(Payment.PaymentMethod.CARD);
        verify(repository).save(any(Payment.class));
        verify(outbox).append(any(Payment.class)); // completed -> outbox event
    }

    @Test
    void bankTransferStaysPendingAndEmitsNothing() {
        Payment result = useCase.process(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("250.00"), Payment.PaymentMethod.BANK_TRANSFER);

        assertThat(result.status()).isEqualTo(Payment.PaymentStatus.PENDING);
        verify(outbox, never()).append(any(Payment.class)); // not settled yet
    }

    @Test
    void cardOverLimitIsDeclined() {
        assertThatThrownBy(() -> useCase.process(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("6000.00"), Payment.PaymentMethod.CARD))
                .isInstanceOf(PaymentDeclinedException.class);
        verify(repository, never()).save(any(Payment.class));
    }

    @Test
    void duplicateOrderIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        Payment existing = new Payment(UUID.randomUUID(), orderId, UUID.randomUUID(),
                new BigDecimal("50.00"), Payment.PaymentMethod.CARD,
                Payment.PaymentStatus.COMPLETED, java.time.Instant.now());
        when(repository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        Payment result = useCase.process(orderId, UUID.randomUUID(),
                new BigDecimal("50.00"), Payment.PaymentMethod.CARD);

        assertThat(result).isEqualTo(existing); // no second charge
        verify(repository, never()).save(any(Payment.class));
        verify(outbox, never()).append(any(Payment.class));
    }

    @Test
    void unsupportedMethodIsRejected() {
        assertThatThrownBy(() -> useCase.process(UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, null))
                .isInstanceOf(Exception.class);
    }
}
