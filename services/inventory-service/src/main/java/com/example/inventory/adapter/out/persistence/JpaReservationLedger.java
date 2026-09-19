package com.example.inventory.adapter.out.persistence;

import com.example.inventory.application.port.ReservationLedger;
import com.example.inventory.domain.ReservationLine;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaReservationLedger implements ReservationLedger {

    private final SpringDataReservationLedgerRepository ledgerRepository;
    private final SpringDataReservationLineRepository lineRepository;

    public JpaReservationLedger(SpringDataReservationLedgerRepository ledgerRepository,
            SpringDataReservationLineRepository lineRepository) {
        this.ledgerRepository = ledgerRepository;
        this.lineRepository = lineRepository;
    }

    @Override
    public Optional<LedgerOutcome> findOutcome(UUID sagaId) {
        return ledgerRepository.findById(sagaId).map(row -> {
            List<ReservationLine> lines = lineRepository.findBySagaId(sagaId).stream()
                    .map(l -> new ReservationLine(l.getProductId(), l.getQuantity()))
                    .toList();
            return new LedgerOutcome(LedgerOutcome.Status.valueOf(row.getStatus()), row.getOrderId(), lines,
                    row.getReason());
        });
    }

    @Override
    public void recordReserved(UUID sagaId, UUID orderId, List<ReservationLine> lines) {
        ReservationLedgerEntity row = new ReservationLedgerEntity();
        row.setSagaId(sagaId);
        row.setOrderId(orderId);
        row.setStatus(LedgerOutcome.Status.RESERVED.name());
        row.setCreatedAt(Instant.now());
        ledgerRepository.save(row);
        for (ReservationLine line : lines) {
            ReservationLineEntity lineRow = new ReservationLineEntity();
            lineRow.setId(UUID.randomUUID());
            lineRow.setSagaId(sagaId);
            lineRow.setProductId(line.productId());
            lineRow.setQuantity(line.quantity());
            lineRepository.save(lineRow);
        }
    }

    @Override
    public void recordRejected(UUID sagaId, UUID orderId, String reason) {
        ReservationLedgerEntity row = new ReservationLedgerEntity();
        row.setSagaId(sagaId);
        row.setOrderId(orderId);
        row.setStatus(LedgerOutcome.Status.REJECTED.name());
        row.setReason(reason);
        row.setCreatedAt(Instant.now());
        ledgerRepository.save(row);
    }

    @Override
    public void markReleased(UUID sagaId) {
        ledgerRepository.findById(sagaId).ifPresent(row -> {
            row.setStatus(LedgerOutcome.Status.RELEASED.name());
            ledgerRepository.save(row);
        });
    }
}
