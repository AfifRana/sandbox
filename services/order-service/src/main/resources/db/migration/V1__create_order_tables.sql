-- Order schema with performance-minded design:
-- surrogate PK, indexed FK columns, money as NUMBER(12,2), timestamps as TIMESTAMP WITH TIME ZONE
CREATE TABLE orders (
    id           RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    customer_id  RAW(16) NOT NULL,
    status       VARCHAR2(20) NOT NULL,
    total_amount NUMBER(12,2) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_created_at  ON orders (created_at DESC);

CREATE TABLE order_lines (
    order_id   RAW(16) NOT NULL,
    product_id RAW(16) NOT NULL,
    quantity   NUMBER(9) NOT NULL CHECK (quantity > 0),
    unit_price NUMBER(12,2) NOT NULL,
    CONSTRAINT fk_order_lines_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_order_lines_order_id ON order_lines (order_id);

-- Transactional outbox for reliable Kafka publishing
CREATE TABLE outbox_events (
    id          RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    aggregate_id RAW(16) NOT NULL,
    type        VARCHAR2(50) NOT NULL,
    payload     VARCHAR2(4000) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    published   NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE published = 0;
