package com.example.product.adapter.in.web;

import com.example.product.application.GetProductUseCase;
import com.example.product.application.SaveProductUseCase;
import com.example.product.domain.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final GetProductUseCase getProduct;
    private final SaveProductUseCase saveProduct;

    public ProductController(GetProductUseCase getProduct, SaveProductUseCase saveProduct) {
        this.getProduct = getProduct;
        this.saveProduct = saveProduct;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> get(@PathVariable UUID id) {
        return getProduct.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = new Product(UUID.randomUUID(), request.name(), request.description(),
                request.price(), request.category());
        return ResponseEntity.status(HttpStatus.CREATED).body(saveProduct.save(product));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable UUID id,
            @Valid @RequestBody CreateProductRequest request) {
        Product product = new Product(id, request.name(), request.description(),
                request.price(), request.category());
        return ResponseEntity.ok(saveProduct.save(product));
    }

    public record CreateProductRequest(
            @NotBlank String name,
            String description,
            @NotNull @Positive BigDecimal price,
            String category) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
