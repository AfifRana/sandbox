package com.example.inventory.application.port;

import com.example.inventory.domain.ReservationLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable idempotency record for saga-driven reservations: one row per
 * sagaId proves whether a reservation was already attempted, so replayed
 * commands (Kafka at-least-once delivery, orchestrator recovery retries)
 * never double-reserve or double-release stock.
 */
public interface ReservationLedger {

    Optional<LedgerOutcome> findOutcome(UUID sagaId);

    void recordReserved(UUID sagaId, UUID orderId, List<ReservationLine> lines);

    void recordRejected(UUID sagaId, UUID orderId, String reason);

    void markReleased(UUID sagaId);

    record LedgerOutcome(Status status, UUID orderId, List<ReservationLine> lines, String reason) {
        public enum Status { RESERVED, REJECTED, RELEASED }
    }
}
