package com.example.product.application;

import com.example.product.application.port.ProductCache;
import com.example.product.application.port.ProductRepository;
import com.example.product.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetProductUseCase {

    private final ProductRepository repository;
    private final ProductCache cache;

    public GetProductUseCase(ProductRepository repository, ProductCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    /**
     * Cache-aside: check cache first, on miss load from DB and populate cache.
     */
    public Optional<Product> getById(UUID id) {
        Optional<Product> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<Product> loaded = repository.findById(id);
        loaded.ifPresent(cache::put);
        return loaded;
    }
}
