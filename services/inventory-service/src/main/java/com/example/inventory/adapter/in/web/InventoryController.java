package com.example.inventory.adapter.in.web;

import com.example.inventory.application.GetStockUseCase;
import com.example.inventory.domain.Stock;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final GetStockUseCase getStock;

    public InventoryController(GetStockUseCase getStock) {
        this.getStock = getStock;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<Stock> getByProductId(@PathVariable UUID productId) {
        return getStock.getByProductId(productId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
