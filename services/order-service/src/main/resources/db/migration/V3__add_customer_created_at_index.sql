-- Align the index with the customer filter and newest-first ordering in
-- list-orders queries. Oracle's optimizer still decides whether this path
-- beats the existing alternatives for a given data distribution.
-- The composite index also covers equality lookups by customer_id, so replace
-- the redundant single-column index rather than keeping two competing paths.
DROP INDEX idx_orders_customer_id;

CREATE INDEX idx_orders_customer_created_at
    ON orders (customer_id, created_at DESC);
