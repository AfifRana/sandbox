package com.example.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.order.application.port.OrderEventOutbox;
import com.example.order.application.port.OrderRepository;
import com.example.order.domain.Order;
import com.example.order.domain.OrderLine;
import java.math.BigDecimal;
import java.util.List;
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

    @InjectMocks CreateOrderUseCase useCase;

    @Test
    void createsOrderWithCorrectTotal() {
        UUID customerId = UUID.randomUUID();
        var lines = List.of(
                new OrderLine(UUID.randomUUID(), 2, new BigDecimal("10.00")),
                new OrderLine(UUID.randomUUID(), 1, new BigDecimal("5.50")));

        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase.create(customerId, lines);

        assertThat(result.totalAmount()).isEqualByComparingTo("25.50");
        assertThat(result.status()).isEqualTo(Order.OrderStatus.CREATED);
    }

    @Test
    void appendsEventToOutboxInSameUseCase() {
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.create(UUID.randomUUID(), List.of(new OrderLine(UUID.randomUUID(), 1, BigDecimal.ONE)));

        verify(eventOutbox).append(any(Order.class));
    }
}
