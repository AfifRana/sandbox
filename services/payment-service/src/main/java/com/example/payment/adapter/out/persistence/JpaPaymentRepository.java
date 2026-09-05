package com.example.payment.adapter.out.persistence;

import com.example.payment.application.port.PaymentRepository;
import com.example.payment.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaPaymentRepository implements PaymentRepository {

    private final SpringDataPaymentRepository jpa;

    public JpaPaymentRepository(SpringDataPaymentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Payment save(Payment payment) {
        return jpa.save(PaymentEntity.fromDomain(payment)).toDomain();
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpa.findById(id).map(PaymentEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return jpa.findByOrderId(orderId).map(PaymentEntity::toDomain);
    }

    @Override
    public boolean existsByOrderId(UUID orderId) {
        return jpa.existsByOrderId(orderId);
    }
}
