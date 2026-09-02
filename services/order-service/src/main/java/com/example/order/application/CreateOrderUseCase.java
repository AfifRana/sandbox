package com.example.order.application;

import com.example.order.application.port.OrderEventOutbox;
import com.example.order.application.port.OrderRepository;
import com.example.order.domain.Order;
import com.example.order.domain.OrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderEventOutbox eventOutbox;

    public CreateOrderUseCase(OrderRepository orderRepository, OrderEventOutbox eventOutbox) {
        this.orderRepository = orderRepository;
        this.eventOutbox = eventOutbox;
    }

    @Transactional
    public Order create(UUID customerId, List<OrderLine> lines) {
        BigDecimal total = lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(UUID.randomUUID(), customerId, lines,
                Order.OrderStatus.CREATED, total, Instant.now());

        Order saved = orderRepository.save(order);
        eventOutbox.append(saved); // same transaction as the save — no dual-write problem
        return saved;
    }
}
