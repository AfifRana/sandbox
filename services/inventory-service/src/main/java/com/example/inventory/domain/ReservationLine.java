package com.example.inventory.domain;

import java.util.UUID;

public record ReservationLine(UUID productId, int quantity) {
    public ReservationLine {
        if (productId == null) throw new IllegalArgumentException("productId must not be null");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }
}
