package com.example.product;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/v1/products")
class ProductController {

    public record Product(UUID id, String name, BigDecimal price) {}

    @GetMapping("/{id}")
    public Product get(@PathVariable UUID id) {
        // TODO: replace with repository + Redis cache
        return new Product(id, "Sample Product", new BigDecimal("19.99"));
    }
}
