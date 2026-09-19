package com.example.fulfillment.application;

import com.example.fulfillment.application.port.FulfillmentEventOutbox;
import com.example.fulfillment.application.port.FulfillmentLedger;
import com.example.fulfillment.application.port.FulfillmentLedger.LedgerOutcome;
import com.example.fulfillment.domain.ShipmentLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ships an order's lines as the final saga step. Idempotent by sagaId, like
 * the other saga participants: a replayed command never re-ships or
 * re-fails, it only re-emits the already-decided outcome.
 *
 * <p>{@link #ALWAYS_FAILS_PRODUCT_ID} is a deterministic sentinel used by
 * the Saga E2E suite to force the post-payment compensation path without
 * relying on flaky external-carrier simulation.
 */
@Service
public class ShipOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(ShipOrderUseCase.class);

    public static final UUID ALWAYS_FAILS_PRODUCT_ID =
            UUID.fromString("77777777-7777-7777-7777-777777777777");

    private final FulfillmentLedger ledger;
    private final FulfillmentEventOutbox eventOutbox;

    public ShipOrderUseCase(FulfillmentLedger ledger, FulfillmentEventOutbox eventOutbox) {
        this.ledger = ledger;
        this.eventOutbox = eventOutbox;
    }

    @Transactional
    public void ship(UUID sagaId, UUID orderId, List<ShipmentLine> lines) {
        Optional<LedgerOutcome> existing = ledger.findOutcome(sagaId);
        if (existing.isPresent()) {
            replayOutcome(sagaId, orderId, existing.get());
            return;
        }

        boolean containsSentinelFailure = lines.stream()
                .anyMatch(line -> line.productId().equals(ALWAYS_FAILS_PRODUCT_ID));
        if (containsSentinelFailure) {
            String reason = "carrier rejected shipment for product " + ALWAYS_FAILS_PRODUCT_ID;
            ledger.recordFailed(sagaId, orderId, reason);
            eventOutbox.appendFailed(sagaId, orderId, reason);
            return;
        }

        ledger.recordShipped(sagaId, orderId);
        eventOutbox.appendShipped(sagaId, orderId);
    }

    private void replayOutcome(UUID sagaId, UUID orderId, LedgerOutcome outcome) {
        log.info("Replaying fulfillment outcome sagaId={} status={}", sagaId, outcome.status());
        switch (outcome.status()) {
            case SHIPPED -> eventOutbox.appendShipped(sagaId, orderId);
            case FAILED -> eventOutbox.appendFailed(sagaId, orderId, outcome.reason());
        }
    }
}
