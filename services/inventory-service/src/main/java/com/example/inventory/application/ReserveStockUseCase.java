package com.example.inventory.application;

import com.example.inventory.application.port.InventoryEventOutbox;
import com.example.inventory.application.port.ReservationLedger;
import com.example.inventory.application.port.ReservationLedger.LedgerOutcome;
import com.example.inventory.application.port.StockRepository;
import com.example.inventory.domain.ReservationLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserves stock for every line of an order as one atomic step: either every
 * line succeeds or none do. A mid-way shortfall rolls back the lines already
 * decremented in this same attempt before anything is durably recorded, so a
 * partially-reserved order is never observable.
 */
@Service
public class ReserveStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReserveStockUseCase.class);

    private final StockRepository stockRepository;
    private final ReservationLedger ledger;
    private final InventoryEventOutbox eventOutbox;

    public ReserveStockUseCase(StockRepository stockRepository, ReservationLedger ledger,
            InventoryEventOutbox eventOutbox) {
        this.stockRepository = stockRepository;
        this.ledger = ledger;
        this.eventOutbox = eventOutbox;
    }

    @Transactional
    public void reserve(UUID sagaId, UUID orderId, List<ReservationLine> lines) {
        Optional<LedgerOutcome> existing = ledger.findOutcome(sagaId);
        if (existing.isPresent()) {
            replayOutcome(sagaId, orderId, existing.get());
            return;
        }

        List<ReservationLine> decremented = new ArrayList<>(lines.size());
        for (ReservationLine line : lines) {
            boolean ok = stockRepository.decrementIfAvailable(line.productId(), line.quantity());
            if (!ok) {
                for (ReservationLine done : decremented) {
                    stockRepository.increment(done.productId(), done.quantity());
                }
                String reason = "insufficient stock for product " + line.productId();
                ledger.recordRejected(sagaId, orderId, reason);
                eventOutbox.appendRejected(sagaId, orderId, reason);
                return;
            }
            decremented.add(line);
        }
        ledger.recordReserved(sagaId, orderId, lines);
        eventOutbox.appendReserved(sagaId, orderId);
    }

    /**
     * A duplicate or recovery-retried command must not reserve or reject
     * twice, but the reply event is re-appended so a lost or unacknowledged
     * first reply still reaches the orchestrator.
     */
    private void replayOutcome(UUID sagaId, UUID orderId, LedgerOutcome outcome) {
        log.info("Replaying inventory reservation outcome sagaId={} status={}", sagaId, outcome.status());
        switch (outcome.status()) {
            case RESERVED -> eventOutbox.appendReserved(sagaId, orderId);
            case REJECTED -> eventOutbox.appendRejected(sagaId, orderId, outcome.reason());
            case RELEASED -> log.info("Saga {} was already released; ignoring reserve replay", sagaId);
        }
    }
}
