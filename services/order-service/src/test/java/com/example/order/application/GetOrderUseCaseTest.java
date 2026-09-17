package com.example.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.order.application.port.OrderRepository;
import com.example.order.domain.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GetOrderUseCaseTest {

    private final OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
    private final GetOrderUseCase useCase = new GetOrderUseCase(orderRepository);

    @Test
    void returnsOrderByIdFromRepository() {
        UUID orderId = UUID.randomUUID();
        Order order = Mockito.mock(Order.class);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThat(useCase.getById(orderId)).containsSame(order);
        verify(orderRepository).findById(orderId);
    }

    @Test
    void returnsRecentCustomerOrdersWithRequestedLimit() {
        UUID customerId = UUID.randomUUID();
        Order first = Mockito.mock(Order.class);
        Order second = Mockito.mock(Order.class);
        List<Order> orders = List.of(first, second);
        when(orderRepository.findRecentByCustomer(customerId, 2)).thenReturn(orders);

        assertThat(useCase.getRecentByCustomer(customerId, 2)).containsExactly(first, second);
        verify(orderRepository).findRecentByCustomer(customerId, 2);
    }
}
