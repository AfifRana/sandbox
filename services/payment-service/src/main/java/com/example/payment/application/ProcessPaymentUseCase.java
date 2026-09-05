package com.example.payment.application;

import com.example.payment.application.port.PaymentEventOutbox;
import com.example.payment.application.port.PaymentRepository;
import com.example.payment.application.strategy.PaymentDeclinedException;
import com.example.payment.application.strategy.PaymentProcessor;
import com.example.payment.domain.Payment;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessPaymentUseCase {

    private final Map<Payment.PaymentMethod, PaymentProcessor> processorsByMethod;
    private final PaymentRepository paymentRepository;
    private final PaymentEventOutbox eventOutbox;

    public ProcessPaymentUseCase(List<PaymentProcessor> processors,
            PaymentRepository paymentRepository,
            PaymentEventOutbox eventOutbox) {
        // Strategy registration: Spring injects every PaymentProcessor bean;
        // dispatch becomes a map lookup — Open/Closed in action.
        this.processorsByMethod = new EnumMap<>(Payment.PaymentMethod.class);
        processors.forEach(p -> processorsByMethod.put(p.supports(), p));
        this.paymentRepository = paymentRepository;
        this.eventOutbox = eventOutbox;
    }

    @Transactional
    public Payment process(UUID orderId, UUID customerId, BigDecimal amount,
            Payment.PaymentMethod method) {
        // Idempotency: one payment per order — replayed requests or duplicate
        // order.created events must not double-charge.
        Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return existing.get();
        }

        PaymentProcessor processor = processorsByMethod.get(method);
        if (processor == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + method);
        }

        Payment charged = processor.charge(orderId, customerId, amount);
        Payment saved = paymentRepository.save(charged);

        if (saved.status() == Payment.PaymentStatus.COMPLETED) {
            eventOutbox.append(saved); // same transaction — no dual-write
        }
        return saved;
    }
}
