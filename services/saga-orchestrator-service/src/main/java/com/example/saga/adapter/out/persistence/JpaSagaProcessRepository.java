package com.example.saga.adapter.out.persistence;

import com.example.saga.application.port.SagaProcessRepository;
import com.example.saga.domain.SagaProcess;
import com.example.saga.domain.SagaStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaSagaProcessRepository implements SagaProcessRepository {

    private final SpringDataSagaProcessRepository repository;

    public JpaSagaProcessRepository(SpringDataSagaProcessRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public SagaProcess save(SagaProcess saga) {
        SagaProcessEntity row = repository.findById(saga.sagaId()).orElseGet(SagaProcessEntity::new);
        row.setSagaId(saga.sagaId());
        row.setOrderId(saga.orderId());
        row.setStatus(saga.status().name());
        row.setReason(saga.reason());
        row.setLines(new java.util.HashMap<>(saga.lines()));
        row.setCreatedAt(saga.createdAt());
        row.setUpdatedAt(saga.updatedAt());
        repository.save(row);
        return saga;
    }

    @Override
    public Optional<SagaProcess> find(UUID sagaId) {
        return repository.findById(sagaId).map(this::toDomain);
    }

    @Override
    public List<SagaProcess> findPending() {
        return repository.findByStatusIn(SagaStatus.STARTED.name(), SagaStatus.INVENTORY_RESERVED.name())
                .stream().map(this::toDomain).toList();
    }

    private SagaProcess toDomain(SagaProcessEntity row) {
        return new SagaProcess(row.getSagaId(), row.getOrderId(), SagaStatus.valueOf(row.getStatus()),
                row.getReason(), row.getLines(), row.getCreatedAt(), row.getUpdatedAt());
    }
}