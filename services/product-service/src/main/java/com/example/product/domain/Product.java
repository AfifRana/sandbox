package com.example.product.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Product(UUID id, String name, String description, BigDecimal price, String category) {
    public Product {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("price must not be negative");
    }
}
