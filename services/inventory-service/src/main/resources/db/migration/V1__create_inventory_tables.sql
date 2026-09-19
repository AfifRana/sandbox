-- Stock is intentionally split from the reservation ledger: `stock` holds
-- the current available/reserved counters, while `reservation_ledger` +
-- `reservation_lines` are the durable idempotency record keyed by sagaId —
-- a replayed or retried command is recognized before it ever touches stock.
CREATE TABLE stock (
    product_id         RAW(16) PRIMARY KEY,
    available_quantity NUMBER(10) NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity  NUMBER(10) NOT NULL CHECK (reserved_quantity >= 0)
);

CREATE TABLE reservation_ledger (
    saga_id    RAW(16) PRIMARY KEY,
    order_id   RAW(16) NOT NULL,
    status     VARCHAR2(20) NOT NULL,
    reason     VARCHAR2(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE reservation_lines (
    id         RAW(16) PRIMARY KEY,
    saga_id    RAW(16) NOT NULL,
    product_id RAW(16) NOT NULL,
    quantity   NUMBER(10) NOT NULL,
    CONSTRAINT fk_reservation_lines_saga FOREIGN KEY (saga_id) REFERENCES reservation_ledger (saga_id)
);

CREATE INDEX idx_reservation_lines_saga_id ON reservation_lines (saga_id);

CREATE TABLE inventory_outbox_events (
    id           RAW(16) PRIMARY KEY,
    aggregate_id RAW(16) NOT NULL,
    type         VARCHAR2(50) NOT NULL,
    payload      VARCHAR2(4000) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published    NUMBER(1) DEFAULT 0 NOT NULL
);

-- Oracle has no partial index; a function-based index only indexes
-- unpublished rows, matching the other services' outbox tables.
CREATE INDEX idx_inventory_outbox_pending ON inventory_outbox_events (CASE WHEN published = 0 THEN created_at END);

-- Seed stock matching product-service's catalog. Two sentinel products make
-- saga failure paths deterministic for E2E tests: product 66666666-...
-- always has zero stock (deterministic inventory-reservation rejection),
-- and 77777777-... has ample stock but fulfillment-service always fails to
-- ship it (deterministic post-payment compensation path).
INSERT INTO stock (product_id, available_quantity, reserved_quantity) VALUES (HEXTORAW('22222222222222222222222222222222'), 1000, 0);
INSERT INTO stock (product_id, available_quantity, reserved_quantity) VALUES (HEXTORAW('33333333333333333333333333333333'), 1000, 0);
INSERT INTO stock (product_id, available_quantity, reserved_quantity) VALUES (HEXTORAW('44444444444444444444444444444444'), 1000, 0);
INSERT INTO stock (product_id, available_quantity, reserved_quantity) VALUES (HEXTORAW('55555555555555555555555555555555'), 1000, 0);
INSERT INTO stock (product_id, available_quantity, reserved_quantity) VALUES (HEXTORAW('66666666666666666666666666666666'), 0, 0);
INSERT INTO stock (product_id, available_quantity, reserved_quantity) VALUES (HEXTORAW('77777777777777777777777777777777'), 500, 0);
