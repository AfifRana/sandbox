-- The fulfillment ledger is the durable idempotency record keyed by sagaId:
-- a replayed or retried ship command is recognized before it ever touches a
-- carrier, exactly like inventory-service's reservation_ledger.
CREATE TABLE fulfillment_ledger (
    saga_id    RAW(16) PRIMARY KEY,
    order_id   RAW(16) NOT NULL,
    status     VARCHAR2(20) NOT NULL,
    reason     VARCHAR2(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_fulfillment_ledger_order ON fulfillment_ledger (order_id);

CREATE TABLE fulfillment_outbox_events (
    id           RAW(16) PRIMARY KEY,
    aggregate_id RAW(16) NOT NULL,
    type         VARCHAR2(50) NOT NULL,
    payload      VARCHAR2(4000) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published    NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE INDEX idx_fulfillment_outbox_pending ON fulfillment_outbox_events (CASE WHEN published = 0 THEN created_at END);