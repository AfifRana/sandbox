package com.example.inventory.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.inventory.application.port.InventoryEventOutbox;
import com.example.inventory.application.port.ReservationLedger;
import com.example.inventory.application.port.ReservationLedger.LedgerOutcome;
import com.example.inventory.application.port.StockRepository;
import com.example.inventory.domain.ReservationLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseStockUseCaseTest {

    @Mock StockRepository stockRepository;
    @Mock ReservationLedger ledger;
    @Mock InventoryEventOutbox eventOutbox;

    ReleaseStockUseCase useCase;

    UUID sagaId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID productA = UUID.randomUUID();

    @Test
    void activeReservationIsReturnedToStockAndMarkedReleased() {
        useCase = new ReleaseStockUseCase(stockRepository, ledger, eventOutbox);
        List<ReservationLine> lines = List.of(new ReservationLine(productA, 4));
        when(ledger.findOutcome(sagaId))
                .thenReturn(Optional.of(new LedgerOutcome(LedgerOutcome.Status.RESERVED, orderId, lines, null)));

        useCase.release(sagaId, orderId);

        verify(stockRepository).increment(productA, 4);
        verify(ledger).markReleased(sagaId);
        verify(eventOutbox).appendReleased(sagaId, orderId);
    }

    @Test
    void alreadyReleasedSagaIsANoOpButStillAcksReply() {
        useCase = new ReleaseStockUseCase(stockRepository, ledger, eventOutbox);
        when(ledger.findOutcome(sagaId))
                .thenReturn(Optional.of(new LedgerOutcome(LedgerOutcome.Status.RELEASED, orderId, List.of(), null)));

        useCase.release(sagaId, orderId);

        verify(stockRepository, never()).increment(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(ledger, never()).markReleased(any());
        verify(eventOutbox).appendReleased(sagaId, orderId);
    }

    @Test
    void unknownSagaIsANoOpButStillAcksReply() {
        useCase = new ReleaseStockUseCase(stockRepository, ledger, eventOutbox);
        when(ledger.findOutcome(sagaId)).thenReturn(Optional.empty());

        useCase.release(sagaId, orderId);

        verify(stockRepository, never()).increment(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(eventOutbox).appendReleased(sagaId, orderId);
    }

    @Test
    void rejectedSagaNeverHadAReservationSoReleaseIsANoOp() {
        useCase = new ReleaseStockUseCase(stockRepository, ledger, eventOutbox);
        when(ledger.findOutcome(sagaId))
                .thenReturn(Optional.of(new LedgerOutcome(LedgerOutcome.Status.REJECTED, orderId, List.of(), "reason")));

        useCase.release(sagaId, orderId);

        verify(stockRepository, never()).increment(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(ledger, never()).markReleased(any());
        verify(eventOutbox).appendReleased(sagaId, orderId);
    }
}
