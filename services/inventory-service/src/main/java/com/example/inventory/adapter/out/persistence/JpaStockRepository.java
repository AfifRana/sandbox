package com.example.inventory.adapter.out.persistence;

import com.example.inventory.application.port.StockRepository;
import com.example.inventory.domain.Stock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaStockRepository implements StockRepository {

    private final SpringDataStockRepository repository;

    public JpaStockRepository(SpringDataStockRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Stock> findById(UUID productId) {
        return repository.findById(productId).map(StockEntity::toDomain);
    }

    @Override
    public boolean decrementIfAvailable(UUID productId, int quantity) {
        return repository.decrementIfAvailable(productId, quantity) == 1;
    }

    @Override
    public void increment(UUID productId, int quantity) {
        repository.increment(productId, quantity);
    }
}
