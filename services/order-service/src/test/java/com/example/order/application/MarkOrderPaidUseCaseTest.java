package com.example.order.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.order.application.port.OrderRepository;
import com.example.order.domain.Order;
import com.example.order.domain.OrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkOrderPaidUseCaseTest {

    @Mock
    OrderRepository repository;

    @Test
    void transitionsCreatedOrderToPaid() {
        UUID orderId = UUID.randomUUID();
        Order created = new Order(orderId, UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("10.00"))),
                Order.OrderStatus.CREATED, new BigDecimal("10.00"), Instant.now());
        when(repository.findById(orderId)).thenReturn(Optional.of(created));

        new MarkOrderPaidUseCase(repository).markPaid(orderId);

        verify(repository).save(any(Order.class));
    }

    @Test
    void replayedEventIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        Order paid = new Order(orderId, UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("10.00"))),
                Order.OrderStatus.PAID, new BigDecimal("10.00"), Instant.now());
        when(repository.findById(orderId)).thenReturn(Optional.of(paid));

        new MarkOrderPaidUseCase(repository).markPaid(orderId);

        verify(repository, never()).save(any(Order.class)); // already PAID — no-op
    }

    @Test
    void unknownOrderIsIgnored() {
        UUID orderId = UUID.randomUUID();
        when(repository.findById(orderId)).thenReturn(Optional.empty());

        new MarkOrderPaidUseCase(repository).markPaid(orderId);

        verify(repository, never()).save(any(Order.class));
    }
}
