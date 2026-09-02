package com.example.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLine(UUID productId, int quantity, BigDecimal unitPrice) {
    public OrderLine {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (unitPrice.signum() < 0) throw new IllegalArgumentException("unitPrice must not be negative");
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
