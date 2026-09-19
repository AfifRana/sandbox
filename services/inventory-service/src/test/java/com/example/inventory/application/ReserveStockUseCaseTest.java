package com.example.inventory.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReserveStockUseCaseTest {

    @Mock StockRepository stockRepository;
    @Mock ReservationLedger ledger;
    @Mock InventoryEventOutbox eventOutbox;

    ReserveStockUseCase useCase;

    UUID sagaId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID productA = UUID.randomUUID();
    UUID productB = UUID.randomUUID();

    @Test
    void allLinesAvailableReservesEveryLineAndPublishesReserved() {
        useCase = new ReserveStockUseCase(stockRepository, ledger, eventOutbox);
        when(ledger.findOutcome(sagaId)).thenReturn(Optional.empty());
        when(stockRepository.decrementIfAvailable(productA, 2)).thenReturn(true);
        when(stockRepository.decrementIfAvailable(productB, 3)).thenReturn(true);
        List<ReservationLine> lines = List.of(new ReservationLine(productA, 2), new ReservationLine(productB, 3));

        useCase.reserve(sagaId, orderId, lines);

        verify(ledger).recordReserved(sagaId, orderId, lines);
        verify(eventOutbox).appendReserved(sagaId, orderId);
        verify(eventOutbox, never()).appendRejected(any(), any(), any());
    }

    @Test
    void insufficientStockOnSecondLineRollsBackFirstLineAndRejects() {
        useCase = new ReserveStockUseCase(stockRepository, ledger, eventOutbox);
        when(ledger.findOutcome(sagaId)).thenReturn(Optional.empty());
        when(stockRepository.decrementIfAvailable(productA, 2)).thenReturn(true);
        when(stockRepository.decrementIfAvailable(productB, 3)).thenReturn(false);
        List<ReservationLine> lines = List.of(new ReservationLine(productA, 2), new ReservationLine(productB, 3));

        useCase.reserve(sagaId, orderId, lines);

        // The already-decremented first line must be given back — no partial
        // reservation may ever be left standing.
        verify(stockRepository).increment(productA, 2);
        verify(ledger).recordRejected(eq(sagaId), eq(orderId), any(String.class));
        verify(eventOutbox).appendRejected(eq(sagaId), eq(orderId), any(String.class));
        verify(ledger, never()).recordReserved(any(), any(), any());
        verify(eventOutbox, never()).appendReserved(any(), any());
    }

    @Test
    void replayedReservedSagaDoesNotDecrementStockAgainButResendsReply() {
        useCase = new ReserveStockUseCase(stockRepository, ledger, eventOutbox);
        List<ReservationLine> lines = List.of(new ReservationLine(productA, 2));
        when(ledger.findOutcome(sagaId))
                .thenReturn(Optional.of(new LedgerOutcome(LedgerOutcome.Status.RESERVED, orderId, lines, null)));

        useCase.reserve(sagaId, orderId, lines);

        verify(stockRepository, never()).decrementIfAvailable(any(), Mockito.anyInt());
        verify(eventOutbox).appendReserved(sagaId, orderId);
    }

    @Test
    void replayedRejectedSagaResendsRejectionWithoutTouchingStock() {
        useCase = new ReserveStockUseCase(stockRepository, ledger, eventOutbox);
        when(ledger.findOutcome(sagaId))
                .thenReturn(Optional.of(new LedgerOutcome(LedgerOutcome.Status.REJECTED, orderId, List.of(), "out of stock")));

        useCase.reserve(sagaId, orderId, List.of(new ReservationLine(productA, 2)));

        verify(stockRepository, never()).decrementIfAvailable(any(), Mockito.anyInt());
        verify(eventOutbox).appendRejected(sagaId, orderId, "out of stock");
    }

    @Test
    void rollbackHappensInDecrementThenIncrementOrder() {
        useCase = new ReserveStockUseCase(stockRepository, ledger, eventOutbox);
        when(ledger.findOutcome(sagaId)).thenReturn(Optional.empty());
        when(stockRepository.decrementIfAvailable(productA, 1)).thenReturn(true);
        when(stockRepository.decrementIfAvailable(productB, 1)).thenReturn(false);

        useCase.reserve(sagaId, orderId,
                List.of(new ReservationLine(productA, 1), new ReservationLine(productB, 1)));

        InOrder inOrder = Mockito.inOrder(stockRepository);
        inOrder.verify(stockRepository).decrementIfAvailable(productA, 1);
        inOrder.verify(stockRepository).decrementIfAvailable(productB, 1);
        inOrder.verify(stockRepository).increment(productA, 1);
        verify(stockRepository, times(1)).increment(any(), Mockito.anyInt());
    }
}
