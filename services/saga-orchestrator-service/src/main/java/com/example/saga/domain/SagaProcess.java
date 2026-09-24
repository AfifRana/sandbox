package com.example.saga.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Durable saga process row: one per order saga, keyed by sagaId. The status
 * transition and the command emission decision live together so a replayed
 * event can never double-emit a command.
 */
public record SagaProcess(
        UUID sagaId,
        UUID orderId,
        SagaStatus status,
        String reason,
        Map<UUID, Integer> lines,
        Instant createdAt,
        Instant updatedAt) {

    public SagaProcess {
        if (sagaId == null) throw new IllegalArgumentException("sagaId must not be null");
        if (orderId == null) throw new IllegalArgumentException("orderId must not be null");
        lines = lines == null ? Map.of() : new LinkedHashMap<>(lines);
    }
}