-- Durable saga process state: the coordinator's single source of truth.
-- Every state transition is an upsert here in the same transaction as the
-- outbox append, so a restart can recover by replaying pending sagas.
CREATE TABLE saga_process (
    saga_id    RAW(16) PRIMARY KEY,
    order_id   RAW(16) NOT NULL,
    status     VARCHAR2(30) NOT NULL,
    reason     VARCHAR2(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE saga_process_lines (
    saga_id    RAW(16) NOT NULL,
    product_id RAW(16) NOT NULL,
    quantity   NUMBER(10) NOT NULL,
    CONSTRAINT fk_saga_lines_saga FOREIGN KEY (saga_id) REFERENCES saga_process (saga_id),
    CONSTRAINT pk_saga_lines PRIMARY KEY (saga_id, product_id)
);

CREATE INDEX idx_saga_process_status ON saga_process (status);

CREATE TABLE saga_outbox_events (
    id           RAW(16) PRIMARY KEY,
    aggregate_id RAW(16) NOT NULL,
    topic        VARCHAR2(50) NOT NULL,
    type         VARCHAR2(50) NOT NULL,
    payload      VARCHAR2(4000) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published    NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE INDEX idx_saga_outbox_pending ON saga_outbox_events (CASE WHEN published = 0 THEN created_at END);