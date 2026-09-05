package com.example.order.application;

import com.example.order.application.port.OrderRepository;
import com.example.order.domain.Order;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkOrderPaidUseCase {

    private final OrderRepository orderRepository;

    public MarkOrderPaidUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Idempotent status transition: CREATED -> PAID. Replayed payment.paid
     * events (at-least-once delivery) are no-ops when the order is already PAID.
     */
    @Transactional
    public void markPaid(UUID orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.status() == Order.OrderStatus.CREATED) {
                Order paid = new Order(order.id(), order.customerId(), order.lines(),
                        Order.OrderStatus.PAID, order.totalAmount(), order.createdAt());
                orderRepository.save(paid);
            }
        });
    }
}
