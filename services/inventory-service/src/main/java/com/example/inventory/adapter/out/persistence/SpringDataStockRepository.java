package com.example.inventory.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataStockRepository extends JpaRepository<StockEntity, UUID> {

    /**
     * Single conditional UPDATE: the WHERE guard makes the decrement atomic
     * at the database row-lock level, so concurrent reservations for the
     * same product cannot both succeed against units that only exist once.
     */
    @Modifying
    @Query("""
            UPDATE StockEntity s
               SET s.availableQuantity = s.availableQuantity - :qty,
                   s.reservedQuantity = s.reservedQuantity + :qty
             WHERE s.productId = :productId AND s.availableQuantity >= :qty
            """)
    int decrementIfAvailable(@Param("productId") UUID productId, @Param("qty") int qty);

    @Modifying
    @Query("""
            UPDATE StockEntity s
               SET s.availableQuantity = s.availableQuantity + :qty,
                   s.reservedQuantity = s.reservedQuantity - :qty
             WHERE s.productId = :productId
            """)
    void increment(@Param("productId") UUID productId, @Param("qty") int qty);
}
