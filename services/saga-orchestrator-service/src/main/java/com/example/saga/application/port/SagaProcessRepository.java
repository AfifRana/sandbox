package com.example.saga.application.port;

import com.example.saga.domain.SagaProcess;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable saga process store. Save must be a full-row upsert so state
 * transitions survive restarts (recovery replays pending sagas from here).
 */
public interface SagaProcessRepository {

    SagaProcess save(SagaProcess saga);

    Optional<SagaProcess> find(UUID sagaId);

    List<SagaProcess> findPending();
}