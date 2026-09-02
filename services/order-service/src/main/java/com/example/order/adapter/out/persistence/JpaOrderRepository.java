package com.example.order.adapter.out.persistence;

import com.example.order.application.port.OrderRepository;
import com.example.order.domain.Order;
import com.example.order.domain.OrderLine;
import com.example.order.domain.Order.OrderStatus;
import jakarta.persistence.CascadeType;
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
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
public class JpaOrderRepository implements OrderRepository {

    private final SpringDataOrderRepository jpa;

    public JpaOrderRepository(SpringDataOrderRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = OrderEntity.fromDomain(order);
        return jpa.save(entity).toDomain();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpa.findById(id).map(OrderEntity::toDomain);
    }

    interface SpringDataOrderRepository extends JpaRepository<OrderEntity, UUID> {}

    @Entity
    @Table(name = "orders")
    static class OrderEntity {
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
    }

    @jakarta.persistence.Embeddable
    static class OrderLineEntity {
        @Column(name = "product_id", nullable = false)
        UUID productId;

        @Column(nullable = false)
        int quantity;

        @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
        BigDecimal unitPrice;

        static OrderLineEntity fromDomain(OrderLine l) {
            OrderLineEntity e = new OrderLineEntity();
            e.productId = l.productId();
            e.quantity = l.quantity();
            e.unitPrice = l.unitPrice();
            return e;
        }

        OrderLine toDomain() {
            return new OrderLine(productId, quantity, unitPrice);
        }
    }
}
