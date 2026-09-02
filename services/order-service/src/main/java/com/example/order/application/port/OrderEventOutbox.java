package com.example.order.application.port;

import com.example.order.domain.Order;

/**
 * Outbox port: events are written transactionally with the aggregate
 * (transactional outbox pattern) and published asynchronously.
 */
public interface OrderEventOutbox {
    void append(Order order);
}
