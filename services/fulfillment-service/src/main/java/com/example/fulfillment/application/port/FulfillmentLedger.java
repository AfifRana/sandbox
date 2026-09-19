package com.example.fulfillment.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Durable idempotency record for saga-driven shipments: one row per sagaId
 * proves whether a shipment was already attempted, so replayed commands
 * (Kafka at-least-once delivery, orchestrator recovery retries) never
 * ship — or re-fail — the same order twice.
 */
public interface FulfillmentLedger {

    Optional<LedgerOutcome> findOutcome(UUID sagaId);

    Optional<LedgerOutcome> findByOrderId(UUID orderId);

    void recordShipped(UUID sagaId, UUID orderId);

    void recordFailed(UUID sagaId, UUID orderId, String reason);

    record LedgerOutcome(Status status, UUID orderId, String reason) {
        public enum Status { SHIPPED, FAILED }
    }
}
