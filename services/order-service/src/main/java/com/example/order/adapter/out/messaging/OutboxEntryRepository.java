package com.example.order.adapter.out.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEntryRepository extends JpaRepository<OutboxEntry, UUID> {
    List<OutboxEntry> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}
