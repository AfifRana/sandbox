package com.example.fulfillment.adapter.out.persistence;

import com.example.fulfillment.application.port.FulfillmentLedger;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaFulfillmentLedger implements FulfillmentLedger {

    private final SpringDataFulfillmentLedgerRepository repository;

    public JpaFulfillmentLedger(SpringDataFulfillmentLedgerRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<LedgerOutcome> findOutcome(UUID sagaId) {
        return repository.findById(sagaId).map(row ->
                new LedgerOutcome(LedgerOutcome.Status.valueOf(row.getStatus()), row.getOrderId(), row.getReason()));
    }

    @Override
    public Optional<LedgerOutcome> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId).map(row ->
                new LedgerOutcome(LedgerOutcome.Status.valueOf(row.getStatus()), row.getOrderId(), row.getReason()));
    }

    @Override
    public void recordShipped(UUID sagaId, UUID orderId) {
        save(sagaId, orderId, LedgerOutcome.Status.SHIPPED, null);
    }

    @Override
    public void recordFailed(UUID sagaId, UUID orderId, String reason) {
        save(sagaId, orderId, LedgerOutcome.Status.FAILED, reason);
    }

    private void save(UUID sagaId, UUID orderId, LedgerOutcome.Status status, String reason) {
        FulfillmentLedgerEntity row = new FulfillmentLedgerEntity();
        row.setSagaId(sagaId);
        row.setOrderId(orderId);
        row.setStatus(status.name());
        row.setReason(reason);
        row.setCreatedAt(Instant.now());
        repository.save(row);
    }
}