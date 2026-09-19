package com.example.inventory.domain;

import java.util.UUID;

public record Stock(UUID productId, int availableQuantity, int reservedQuantity) {
    public Stock {
        if (productId == null) throw new IllegalArgumentException("productId must not be null");
        if (availableQuantity < 0) throw new IllegalArgumentException("availableQuantity must not be negative");
        if (reservedQuantity < 0) throw new IllegalArgumentException("reservedQuantity must not be negative");
    }
}
