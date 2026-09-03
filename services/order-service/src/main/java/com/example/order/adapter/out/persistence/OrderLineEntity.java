package com.example.order.adapter.out.persistence;

import com.example.order.domain.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.UUID;

@Embeddable
public class OrderLineEntity {

    @Column(name = "product_id", nullable = false)
    UUID productId;

    @Column(nullable = false)
    int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    BigDecimal unitPrice;

    static OrderLineEntity fromDomain(OrderLine l) {
        OrderLineEntity e = new OrderLineEntity();
        e.productId = l.productId();
        e.quantity = l.quantity();
        e.unitPrice = l.unitPrice();
        return e;
    }

    OrderLine toDomain() {
        return new OrderLine(productId, quantity, unitPrice);
    }
}
