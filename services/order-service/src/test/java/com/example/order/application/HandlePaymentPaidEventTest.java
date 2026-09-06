package com.example.order.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.order.application.port.OrderRepository;
import com.example.order.application.port.ProcessedEventStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandlePaymentPaidEventTest {

    @Mock
    ProcessedEventStore processedEvents;

    @Mock
    MarkOrderPaidUseCase markOrderPaid;

    @InjectMocks
    HandlePaymentPaidEvent handler;

    @Test
    void newEventClaimsInboxAndMarksOrderPaid() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(processedEvents.claim(paymentId, "order-service")).thenReturn(true);

        handler.handle(paymentId, orderId);

        verify(markOrderPaid).markPaid(orderId);
    }

    @Test
    void duplicateEventIsSkippedBeforeAnyStateChange() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(processedEvents.claim(paymentId, "order-service")).thenReturn(false);

        handler.handle(paymentId, orderId);

        verify(markOrderPaid, never()).markPaid(any(UUID.class));
    }
}
