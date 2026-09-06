package com.example.order.application;

import com.example.order.application.port.ProcessedEventStore;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles a payment.paid event exactly once: the inbox claim and the
 * CREATED -> PAID transition commit in the same DB transaction. Replayed
 * events (at-least-once Kafka delivery) are skipped before any state change.
 */
@Service
public class HandlePaymentPaidEvent {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymentPaidEvent.class);
    private static final String CONSUMER_GROUP = "order-service";

    private final ProcessedEventStore processedEvents;
    private final MarkOrderPaidUseCase markOrderPaid;

    public HandlePaymentPaidEvent(ProcessedEventStore processedEvents, MarkOrderPaidUseCase markOrderPaid) {
        this.processedEvents = processedEvents;
        this.markOrderPaid = markOrderPaid;
    }

    @Transactional
    public void handle(UUID paymentId, UUID orderId) {
        if (!processedEvents.claim(paymentId, CONSUMER_GROUP)) {
            log.debug("Skipping duplicate payment.paid event paymentId={} orderId={}", paymentId, orderId);
            return;
        }
        markOrderPaid.markPaid(orderId);
    }
}
