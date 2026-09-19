package com.example.inventory.adapter.out.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InventoryOutboxEntryRepository extends JpaRepository<InventoryOutboxEntry, UUID> {
    List<InventoryOutboxEntry> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}
