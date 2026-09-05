-- Payment schema: one row per payment, unique order_id enforces the
-- "one payment per order" idempotency rule at the database level.
CREATE TABLE payments (
    id          RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    order_id    RAW(16) NOT NULL UNIQUE,
    customer_id RAW(16) NOT NULL,
    amount      NUMBER(12,2) NOT NULL CHECK (amount > 0),
    method      VARCHAR2(20) NOT NULL,
    status      VARCHAR2(20) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payments_customer_id ON payments (customer_id);

-- Transactional outbox for payment events (same pattern as order-service)
CREATE TABLE payment_outbox_events (
    id          RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    aggregate_id RAW(16) NOT NULL,
    type        VARCHAR2(50) NOT NULL,
    payload     VARCHAR2(4000) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    published   NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE INDEX idx_payment_outbox_pending ON payment_outbox_events (CASE WHEN published = 0 THEN created_at END);
