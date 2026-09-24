package com.example.saga.application.port;

import java.util.Map;
import java.util.UUID;

/**
 * Outbound saga commands. Implementations write to a transactional outbox so
 * a command is emitted exactly as many times as the state machine decides —
 * recovery re-issues pending commands idempotently because participants
 * deduplicate by sagaId.
 */
public interface SagaCommandPublisher {

    void sendInventoryReserve(UUID sagaId, UUID orderId, Map<UUID, Integer> lines);

    void sendInventoryRelease(UUID sagaId, UUID orderId);

    void sendFulfillmentShip(UUID sagaId, UUID orderId, Map<UUID, Integer> lines);
}