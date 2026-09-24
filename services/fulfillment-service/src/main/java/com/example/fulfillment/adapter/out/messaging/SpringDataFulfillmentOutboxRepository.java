package com.example.fulfillment.adapter.out.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFulfillmentOutboxRepository extends JpaRepository<FulfillmentOutboxEntry, UUID> {
    List<FulfillmentOutboxEntry> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}