package com.example.product.adapter.out.persistence;

import com.example.product.application.port.ProductRepository;
import com.example.product.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaProductRepository implements ProductRepository {

    private final SpringDataProductRepository jpa;

    public JpaProductRepository(SpringDataProductRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Product save(Product product) {
        return jpa.save(ProductEntity.fromDomain(product)).toDomain();
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpa.findById(id).map(ProductEntity::toDomain);
    }

    @Override
    public List<Product> findByCategory(String category) {
        return jpa.findByCategory(category).stream().map(ProductEntity::toDomain).toList();
    }
}
