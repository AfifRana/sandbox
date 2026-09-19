package com.example.fulfillment.application;

import com.example.fulfillment.application.port.FulfillmentLedger;
import com.example.fulfillment.application.port.FulfillmentLedger.LedgerOutcome;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetFulfillmentUseCase {

    private final FulfillmentLedger ledger;

    public GetFulfillmentUseCase(FulfillmentLedger ledger) {
        this.ledger = ledger;
    }

    public Optional<LedgerOutcome> getByOrderId(UUID orderId) {
        return ledger.findByOrderId(orderId);
    }
}
