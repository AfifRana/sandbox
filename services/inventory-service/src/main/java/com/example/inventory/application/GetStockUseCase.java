package com.example.inventory.application;

import com.example.inventory.application.port.StockRepository;
import com.example.inventory.domain.Stock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetStockUseCase {

    private final StockRepository stockRepository;

    public GetStockUseCase(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public Optional<Stock> getByProductId(UUID productId) {
        return stockRepository.findById(productId);
    }
}
