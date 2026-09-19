package com.example.fulfillment.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.fulfillment.application.port.FulfillmentEventOutbox;
import com.example.fulfillment.application.port.FulfillmentLedger;
import com.example.fulfillment.application.port.FulfillmentLedger.LedgerOutcome;
import com.example.fulfillment.domain.ShipmentLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShipOrderUseCaseTest {

    @Mock FulfillmentLedger ledger;
    @Mock FulfillmentEventOutbox eventOutbox;

    ShipOrderUseCase useCase;

    UUID sagaId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID productA = UUID.randomUUID();

    @Test
    void ordinaryLinesShipSuccessfullyAndPublishShipped() {
        useCase = new ShipOrderUseCase(ledger, eventOutbox);
        when(ledger.findOutcome(sagaId)).thenReturn(Optional.empty());

        useCase.ship(sagaId, orderId, List.of(new ShipmentLine(productA, 2)));

        verify(ledger).recordShipped(sagaId, orderId);
        verify(eventOutbox).appendShipped(sagaId, orderId);
        verify(eventOutbox, never()).appendFailed(any(), any(), any());
    }

    @Test
    void sentinelAlwaysFailingProductRecordsFailedAndPublishesFailed() {
        useCase = new ShipOrderUseCase(ledger, eventOutbox);
        when(ledger.findOutcome(sagaId)).thenReturn(Optional.empty());
        List<ShipmentLine> lines = List.of(new ShipmentLine(ShipOrderUseCase.ALWAYS_FAILS_PRODUCT_ID, 1));

        useCase.ship(sagaId, orderId, lines);

        verify(ledger).recordFailed(eq(sagaId), eq(orderId), any(String.class));
        verify(eventOutbox).appendFailed(eq(sagaId), eq(orderId), any(String.class));
        verify(ledger, never()).recordShipped(any(), any());
        verify(eventOutbox, never()).appendShipped(any(), any());
    }

    @Test
    void replayedShippedSagaDoesNotReprocessButResendsShippedReply() {
        useCase = new ShipOrderUseCase(ledger, eventOutbox);
        when(ledger.findOutcome(sagaId))
                .thenReturn(Optional.of(new LedgerOutcome(LedgerOutcome.Status.SHIPPED, orderId, null)));

        useCase.ship(sagaId, orderId, List.of(new ShipmentLine(productA, 1)));

        verify(ledger, never()).recordShipped(any(), any());
        verify(eventOutbox).appendShipped(sagaId, orderId);
    }

    @Test
    void replayedFailedSagaResendsFailedReasonWithoutReprocessing() {
        useCase = new ShipOrderUseCase(ledger, eventOutbox);
        when(ledger.findOutcome(sagaId))
                .thenReturn(Optional.of(new LedgerOutcome(LedgerOutcome.Status.FAILED, orderId, "carrier rejected shipment")));

        useCase.ship(sagaId, orderId, List.of(new ShipmentLine(productA, 1)));

        verify(ledger, never()).recordFailed(any(), any(), any());
        verify(eventOutbox).appendFailed(sagaId, orderId, "carrier rejected shipment");
    }
}
