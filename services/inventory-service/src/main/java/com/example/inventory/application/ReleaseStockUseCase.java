package com.example.inventory.application;

import com.example.inventory.application.port.InventoryEventOutbox;
import com.example.inventory.application.port.ReservationLedger;
import com.example.inventory.application.port.ReservationLedger.LedgerOutcome;
import com.example.inventory.application.port.StockRepository;
import com.example.inventory.domain.ReservationLine;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compensating action: returns previously reserved stock. Idempotent by
 * design — a replayed or duplicate release command against an
 * already-released, rejected, or unknown saga is a safe no-op, and the reply
 * event is still (re-)emitted so the orchestrator's recovery sweep converges.
 */
@Service
public class ReleaseStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReleaseStockUseCase.class);

    private final StockRepository stockRepository;
    private final ReservationLedger ledger;
    private final InventoryEventOutbox eventOutbox;

    public ReleaseStockUseCase(StockRepository stockRepository, ReservationLedger ledger,
            InventoryEventOutbox eventOutbox) {
        this.stockRepository = stockRepository;
        this.ledger = ledger;
        this.eventOutbox = eventOutbox;
    }

    @Transactional
    public void release(UUID sagaId, UUID orderId) {
        Optional<LedgerOutcome> outcome = ledger.findOutcome(sagaId);
        if (outcome.isEmpty() || outcome.get().status() != LedgerOutcome.Status.RESERVED) {
            log.info("Release skipped for saga {} — no active reservation (status={})",
                    sagaId, outcome.map(LedgerOutcome::status).orElse(null));
            eventOutbox.appendReleased(sagaId, orderId);
            return;
        }
        for (ReservationLine line : outcome.get().lines()) {
            stockRepository.increment(line.productId(), line.quantity());
        }
        ledger.markReleased(sagaId);
        eventOutbox.appendReleased(sagaId, orderId);
    }
}
