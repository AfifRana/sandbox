package com.example.fulfillment.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFulfillmentLedgerRepository extends JpaRepository<FulfillmentLedgerEntity, UUID> {
    Optional<FulfillmentLedgerEntity> findByOrderId(UUID orderId);
}