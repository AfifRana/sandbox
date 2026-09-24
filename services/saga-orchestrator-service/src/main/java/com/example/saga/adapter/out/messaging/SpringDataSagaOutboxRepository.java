package com.example.saga.adapter.out.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSagaOutboxRepository extends JpaRepository<SagaOutboxEntry, UUID> {
    List<SagaOutboxEntry> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}