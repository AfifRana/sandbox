package com.example.payment.adapter.out.persistence;

import com.example.payment.domain.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    UUID id;

    @Column(name = "order_id", nullable = false)
    UUID orderId;

    @Column(name = "customer_id", nullable = false)
    UUID customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    Payment.PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    Payment.PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    static PaymentEntity fromDomain(Payment payment) {
        PaymentEntity e = new PaymentEntity();
        e.id = payment.id();
        e.orderId = payment.orderId();
        e.customerId = payment.customerId();
        e.amount = payment.amount();
        e.method = payment.method();
        e.status = payment.status();
        e.createdAt = payment.createdAt();
        return e;
    }

    Payment toDomain() {
        return new Payment(id, orderId, customerId, amount, method, status, createdAt);
    }
}
