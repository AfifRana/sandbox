package com.example.inventory.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReservationLedgerRepository extends JpaRepository<ReservationLedgerEntity, UUID> {}

interface SpringDataReservationLineRepository extends JpaRepository<ReservationLineEntity, UUID> {
    List<ReservationLineEntity> findBySagaId(UUID sagaId);
}
