package com.example.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Order(
        UUID id,
        UUID customerId,
        List<OrderLine> lines,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt
) {
    public Order {
        lines = List.copyOf(lines);
    }

    public enum OrderStatus { CREATED, PAID, SHIPPED, CANCELLED }
}
