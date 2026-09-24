package com.example.saga.adapter.out.persistence;

import jakarta.persistence.MapKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSagaProcessRepository extends JpaRepository<SagaProcessEntity, UUID> {
    List<SagaProcessEntity> findByStatusIn(String... statuses);
}