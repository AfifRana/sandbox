package com.example.product.application.port;

import com.example.product.domain.Product;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache-aside port: the application checks the cache before hitting the
 * database and populates it on miss. Adapters decide the actual store (Redis).
 */
public interface ProductCache {
    Optional<Product> get(UUID id);
    void put(Product product);
    void evict(UUID id);
}
