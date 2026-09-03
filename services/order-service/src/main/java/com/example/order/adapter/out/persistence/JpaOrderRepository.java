package com.example.order.adapter.out.persistence;

import com.example.order.application.port.OrderRepository;
import com.example.order.domain.Order;
import java.util.Optional;
import java.util.UUID;
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
}
