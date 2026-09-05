package com.example.order.application;

import com.example.order.application.port.OrderRepository;
import com.example.order.domain.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetOrderUseCase {

    private final OrderRepository orderRepository;

    public GetOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Optional<Order> getById(UUID id) {
        return orderRepository.findById(id);
    }
}
