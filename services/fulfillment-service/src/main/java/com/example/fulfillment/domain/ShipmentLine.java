package com.example.fulfillment.domain;

import java.util.UUID;

public record ShipmentLine(UUID productId, int quantity) {
    public ShipmentLine {
        if (productId == null) throw new IllegalArgumentException("productId must not be null");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }
}
