package com.example.order.adapter.out.persistence;

import com.example.order.domain.Order;
import com.example.order.domain.OrderLine;
import com.example.order.domain.Order.OrderStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    UUID id;

    @Column(name = "customer_id", nullable = false)
    UUID customerId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    List<OrderLineEntity> lines = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    static OrderEntity fromDomain(Order order) {
        OrderEntity e = new OrderEntity();
        e.id = order.id();
        e.customerId = order.customerId();
        e.status = order.status();
        e.totalAmount = order.totalAmount();
        e.createdAt = order.createdAt();
        order.lines().forEach(l -> e.lines.add(OrderLineEntity.fromDomain(l)));
        return e;
    }

    Order toDomain() {
        return new Order(id, customerId,
                lines.stream().map(OrderLineEntity::toDomain).toList(),
                status, totalAmount, createdAt);
    }

    void addLine(OrderLineEntity line) {
        lines.add(line);
    }
}
