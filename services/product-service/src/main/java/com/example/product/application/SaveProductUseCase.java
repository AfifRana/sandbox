package com.example.product.application;

import com.example.product.application.port.ProductCache;
import com.example.product.application.port.ProductRepository;
import com.example.product.domain.Product;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaveProductUseCase {

    private final ProductRepository repository;
    private final ProductCache cache;

    public SaveProductUseCase(ProductRepository repository, ProductCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Transactional
    public Product save(Product product) {
        Product saved = repository.save(product);
        cache.evict(saved.id()); // invalidate stale entry — next read repopulates
        return saved;
    }
}
