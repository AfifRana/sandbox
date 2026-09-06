package com.example.order.application;

import com.example.order.application.port.OrderEventOutbox;
import com.example.order.application.port.OrderRepository;
import com.example.order.application.port.ProductCatalog;
import com.example.order.domain.Order;
import com.example.order.domain.OrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);

    private final OrderRepository orderRepository;
    private final OrderEventOutbox eventOutbox;
    private final ProductCatalog productCatalog;

    public CreateOrderUseCase(OrderRepository orderRepository, OrderEventOutbox eventOutbox,
            ProductCatalog productCatalog) {
        this.orderRepository = orderRepository;
        this.eventOutbox = eventOutbox;
        this.productCatalog = productCatalog;
    }

    @Transactional
    public Order create(UUID customerId, List<OrderLine> requestedLines) {
        List<OrderLine> lines = priceFromCatalog(requestedLines);
        BigDecimal total = lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(UUID.randomUUID(), customerId, lines,
                Order.OrderStatus.CREATED, total, Instant.now());

        Order saved = orderRepository.save(order);
        eventOutbox.append(saved); // same transaction as the save — no dual-write problem
        return saved;
    }

    /**
     * Unknown products fail closed (400): ordering something that does not
     * exist must not succeed. Catalog outages fail open (client price kept,
     * with a warning): keeping the write path up during product-service
     * trouble is a deliberate availability-over-consistency trade-off.
     */
    private List<OrderLine> priceFromCatalog(List<OrderLine> requestedLines) {
        List<OrderLine> lines = new ArrayList<>(requestedLines.size());
        for (OrderLine requested : requestedLines) {
            BigDecimal authoritative = null;
            boolean catalogAnswered = false;
            try {
                authoritative = productCatalog.priceFor(requested.productId()).orElse(null);
                catalogAnswered = true;
            } catch (ProductCatalog.CatalogUnavailableException e) {
                log.warn("Failing open: keeping client price for product {}: {}",
                        requested.productId(), e.getMessage());
            }
            if (catalogAnswered && authoritative == null) {
                throw new IllegalArgumentException(
                        "product " + requested.productId() + " does not exist");
            }
            BigDecimal unitPrice = authoritative != null ? authoritative : requested.unitPrice();
            lines.add(new OrderLine(requested.productId(), requested.quantity(), unitPrice));
        }
        return lines;
    }
}
