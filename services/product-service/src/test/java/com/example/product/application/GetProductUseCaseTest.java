package com.example.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.product.application.port.ProductCache;
import com.example.product.application.port.ProductRepository;
import com.example.product.domain.Product;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProductUseCaseTest {

    @Mock ProductRepository repository;
    @Mock ProductCache cache;

    @InjectMocks GetProductUseCase useCase;

    private final Product product = new Product(UUID.randomUUID(), "Keyboard", "Mechanical",
            new BigDecimal("89.99"), "peripherals");

    @Test
    void cacheHitSkipsDatabase() {
        when(cache.get(product.id())).thenReturn(Optional.of(product));

        Optional<Product> result = useCase.getById(product.id());

        assertThat(result).contains(product);
        verify(repository, never()).findById(any());
    }

    @Test
    void cacheMissLoadsFromDbAndPopulatesCache() {
        when(cache.get(product.id())).thenReturn(Optional.empty());
        when(repository.findById(product.id())).thenReturn(Optional.of(product));

        Optional<Product> result = useCase.getById(product.id());

        assertThat(result).contains(product);
        verify(cache).put(product);
    }

    @Test
    void cacheMissAndDbMissDoesNotPopulateCache() {
        when(cache.get(product.id())).thenReturn(Optional.empty());
        when(repository.findById(product.id())).thenReturn(Optional.empty());

        Optional<Product> result = useCase.getById(product.id());

        assertThat(result).isEmpty();
        verify(cache, never()).put(any());
    }
}
