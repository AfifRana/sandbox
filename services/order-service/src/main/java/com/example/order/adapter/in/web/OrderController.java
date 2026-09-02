package com.example.order.adapter.in.web;

import com.example.order.application.CreateOrderUseCase;
import com.example.order.domain.Order;
import com.example.order.domain.OrderLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrder;

    public OrderController(CreateOrderUseCase createOrder) {
        this.createOrder = createOrder;
    }

    @PostMapping
    public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest request) {
        List<OrderLine> lines = request.lines().stream()
                .map(l -> new OrderLine(l.productId(), l.quantity(), l.unitPrice()))
                .toList();
        Order order = createOrder.create(request.customerId(), lines);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    public record CreateOrderRequest(
            @NotNull UUID customerId,
            @NotEmpty @Valid List<LineRequest> lines) {

        public record LineRequest(
                @NotNull UUID productId,
                @Positive int quantity,
                @NotNull @Positive BigDecimal unitPrice) {}
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
