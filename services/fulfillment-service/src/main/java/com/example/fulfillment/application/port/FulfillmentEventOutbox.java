package com.example.fulfillment.application.port;

import java.util.UUID;

public interface FulfillmentEventOutbox {
    void appendShipped(UUID sagaId, UUID orderId);
    void appendFailed(UUID sagaId, UUID orderId, String reason);
}
