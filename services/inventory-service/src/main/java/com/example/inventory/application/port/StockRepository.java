package com.example.inventory.application.port;

import com.example.inventory.domain.Stock;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    Optional<Stock> findById(UUID productId);

    /**
     * Atomically reserves {@code quantity} units for {@code productId} only if
     * enough stock is currently available right now. Implementations must use
     * a single conditional UPDATE (WHERE available_quantity >= quantity) so
     * concurrent callers racing for the same units cannot both succeed —
     * this is what prevents oversell without a pessimistic lock.
     *
     * @return true if the reservation succeeded
     */
    boolean decrementIfAvailable(UUID productId, int quantity);

    /** Returns previously reserved units back to available stock. */
    void increment(UUID productId, int quantity);
}
