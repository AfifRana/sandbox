package com.example.saga.application;

import com.example.saga.application.port.SagaCommandPublisher;
import com.example.saga.application.port.SagaProcessRepository;
import com.example.saga.domain.SagaProcess;
import com.example.saga.domain.SagaStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrated order saga: reserve inventory → ship → complete, with
 * inventory-release compensation when fulfillment fails after a successful
 * reservation.
 *
 * <p>Idempotency contract: every event handler reloads the durable process
 * row and acts only when the event is consistent with the row's current
 * state; late/duplicate events for unknown or terminal sagas are no-ops.
 * Commands are emitted through a transactional outbox, and recovery
 * re-issues the pending command for non-terminal sagas — safe because every
 * participant deduplicates by sagaId.
 */
@Service
public class OrderSagaUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaUseCase.class);

    private final SagaProcessRepository repository;
    private final SagaCommandPublisher publisher;

    public OrderSagaUseCase(SagaProcessRepository repository, SagaCommandPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Transactional
    public UUID start(UUID orderId, Map<UUID, Integer> lines) {
        UUID sagaId = UUID.randomUUID();
        SagaProcess saga = new SagaProcess(sagaId, orderId, SagaStatus.STARTED, null, lines,
                Instant.now(), Instant.now());
        repository.save(saga);
        publisher.sendInventoryReserve(sagaId, orderId, lines);
        return sagaId;
    }

    @Transactional
    public void onInventoryReserved(UUID sagaId) {
        repository.find(sagaId).ifPresent(saga -> {
            if (saga.status() != SagaStatus.STARTED) {
                log.info("Ignoring inventory.reserved for sagaId={} in status={}", sagaId, saga.status());
                return;
            }
            SagaProcess reserved = withStatus(saga, SagaStatus.INVENTORY_RESERVED);
            repository.save(reserved);
            publisher.sendFulfillmentShip(sagaId, saga.orderId(), saga.lines());
        });
    }

    @Transactional
    public void onInventoryRejected(UUID sagaId, String reason) {
        repository.find(sagaId).ifPresent(saga -> {
            if (saga.status() != SagaStatus.STARTED) {
                log.info("Ignoring inventory.reservation.rejected for sagaId={} in status={}", sagaId, saga.status());
                return;
            }
            repository.save(withStatus(saga, SagaStatus.REJECTED, reason));
        });
    }

    @Transactional
    public void onFulfillmentShipped(UUID sagaId) {
        repository.find(sagaId).ifPresent(saga -> {
            if (saga.status() != SagaStatus.INVENTORY_RESERVED) {
                log.info("Ignoring fulfillment.shipped for sagaId={} in status={}", sagaId, saga.status());
                return;
            }
            repository.save(withStatus(saga, SagaStatus.COMPLETED));
        });
    }

    @Transactional
    public void onFulfillmentFailed(UUID sagaId, String reason) {
        repository.find(sagaId).ifPresent(saga -> {
            if (saga.status() != SagaStatus.INVENTORY_RESERVED) {
                log.info("Ignoring fulfillment.failed for sagaId={} in status={}", sagaId, saga.status());
                return;
            }
            // Compensation: release the inventory reserved for this saga, then
            // close the saga. A duplicate failed event re-enters this branch
            // only from INVENTORY_RESERVED, so the release command is sent once.
            publisher.sendInventoryRelease(sagaId, saga.orderId());
            repository.save(withStatus(saga, SagaStatus.COMPENSATED, reason));
        });
    }

    /**
     * Restart recovery: re-issue the pending command for every non-terminal
     * saga. Participants deduplicate by sagaId, so re-sending is safe.
     */
    @Transactional
    public void recoverPending() {
        repository.findPending().forEach(saga -> {
            switch (saga.status()) {
                case STARTED -> publisher.sendInventoryReserve(saga.sagaId(), saga.orderId(), saga.lines());
                case INVENTORY_RESERVED -> publisher.sendFulfillmentShip(saga.sagaId(), saga.orderId(), saga.lines());
                default -> log.info("Nothing to recover for sagaId={} in status={}", saga.sagaId(), saga.status());
            }
        });
    }

    private SagaProcess withStatus(SagaProcess saga, SagaStatus status) {
        return withStatus(saga, status, saga.reason());
    }

    private SagaProcess withStatus(SagaProcess saga, SagaStatus status, String reason) {
        return new SagaProcess(saga.sagaId(), saga.orderId(), status, reason, saga.lines(),
                saga.createdAt(), Instant.now());
    }
}