package com.example.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.example.product.application.port.ProductCache;
import com.example.product.application.port.ProductRepository;
import com.example.product.domain.Product;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaveProductUseCaseTest {

    @Mock ProductRepository repository;
    @Mock ProductCache cache;

    @InjectMocks SaveProductUseCase useCase;

    @Test
    void saveEvictsStaleCacheEntry() {
        Product product = new Product(UUID.randomUUID(), "Mouse", "Wireless",
                new BigDecimal("59.99"), "peripherals");
        org.mockito.Mockito.when(repository.save(product)).thenReturn(product);

        Product result = useCase.save(product);

        assertThat(result).isEqualTo(product);
        verify(cache).evict(product.id());
    }
}
