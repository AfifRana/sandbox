package com.example.order.application.port;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to the product catalog (owned by product-service). Used to
 * validate ordered products and fetch authoritative prices, so clients
 * cannot set arbitrary unit prices on order lines.
 */
public interface ProductCatalog {

    /**
     * @return the authoritative price, or empty if the product does not exist
     */
    Optional<BigDecimal> priceFor(UUID productId);

    /**
     * Thrown when the catalog is unreachable after retries and the circuit
     * is open — callers decide whether to fail open or closed.
     */
    class CatalogUnavailableException extends RuntimeException {
        public CatalogUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
