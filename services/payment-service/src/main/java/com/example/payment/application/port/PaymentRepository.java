package com.example.payment.application.port;

import com.example.payment.domain.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
