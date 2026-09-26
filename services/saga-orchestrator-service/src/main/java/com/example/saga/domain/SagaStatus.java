package com.example.saga.domain;

import java.util.UUID;

/**
 * Explicit saga states. STARTED → INVENTORY_RESERVED → COMPLETED is the
 * happy path; INVENTORY_RESERVED → COMPENSATED is the fulfillment-failure
 * compensation path — the inventory-release command is issued in the same
 * transition, so there is no separate COMPENSATING state to wait in;
 * STARTED → REJECTED is a reservation-rejection short circuit.
 */
public enum SagaStatus {
    STARTED(false),
    INVENTORY_RESERVED(false),
    COMPLETED(true),
    REJECTED(true),
    COMPENSATED(true);

    private final boolean terminal;

    SagaStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}