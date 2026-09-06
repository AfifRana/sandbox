package com.example.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.order.application.port.OrderEventOutbox;
import com.example.order.application.port.OrderRepository;
import com.example.order.application.port.ProductCatalog;
import com.example.order.domain.Order;
import com.example.order.domain.OrderLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderEventOutbox eventOutbox;
    @Mock ProductCatalog productCatalog;

    @InjectMocks CreateOrderUseCase useCase;

    @Test
    void createsOrderWithCorrectTotal() {
        UUID customerId = UUID.randomUUID();
        var lines = List.of(
                new OrderLine(UUID.randomUUID(), 2, new BigDecimal("10.00")),
                new OrderLine(UUID.randomUUID(), 1, new BigDecimal("5.50")));
        lines.forEach(l -> when(productCatalog.priceFor(l.productId()))
                .thenReturn(Optional.of(l.unitPrice())));

        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase.create(customerId, lines);

        assertThat(result.totalAmount()).isEqualByComparingTo("25.50");
        assertThat(result.status()).isEqualTo(Order.OrderStatus.CREATED);
    }

    @Test
    void appendsEventToOutboxInSameUseCase() {
        UUID productId = UUID.randomUUID();
        when(productCatalog.priceFor(productId)).thenReturn(Optional.of(BigDecimal.ONE));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.create(UUID.randomUUID(), List.of(new OrderLine(productId, 1, BigDecimal.ONE)));

        verify(eventOutbox).append(any(Order.class));
    }

    @Test
    void usesAuthoritativeCatalogPriceOverClientPrice() {
        UUID productId = UUID.randomUUID();
        when(productCatalog.priceFor(productId)).thenReturn(Optional.of(new BigDecimal("99.99")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase.create(UUID.randomUUID(),
                List.of(new OrderLine(productId, 1, new BigDecimal("1.00"))));

        assertThat(result.lines().get(0).unitPrice()).isEqualByComparingTo("99.99");
        assertThat(result.totalAmount()).isEqualByComparingTo("99.99");
    }

    @Test
    void unknownProductFailsClosed() {
        UUID productId = UUID.randomUUID();
        when(productCatalog.priceFor(productId)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        useCase.create(UUID.randomUUID(),
                                List.of(new OrderLine(productId, 1, BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
        verify(orderRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void catalogOutageFailsOpenWithClientPrice() {
        UUID productId = UUID.randomUUID();
        when(productCatalog.priceFor(productId))
                .thenThrow(new ProductCatalog.CatalogUnavailableException("circuit open", null));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase.create(UUID.randomUUID(),
                List.of(new OrderLine(productId, 2, new BigDecimal("7.77"))));

        assertThat(result.lines().get(0).unitPrice()).isEqualByComparingTo("7.77");
        assertThat(result.totalAmount()).isEqualByComparingTo("15.54");
    }
}
