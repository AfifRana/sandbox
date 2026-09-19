package com.example.inventory.application.port;

import java.util.UUID;

public interface InventoryEventOutbox {
    void appendReserved(UUID sagaId, UUID orderId);
    void appendRejected(UUID sagaId, UUID orderId, String reason);
    void appendReleased(UUID sagaId, UUID orderId);
}
