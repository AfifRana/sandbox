-- Deterministic sentinel products used by the Saga orchestration E2E scenarios.
-- 66666666...: zero stock in inventory-service -> always triggers reservation rejection.
-- 77777777...: ample stock in inventory-service but fulfillment-service always fails shipment for it
--              -> always triggers the post-payment compensation chain.
INSERT INTO products (id, name, description, price, category) VALUES (HEXTORAW('66666666666666666666666666666666'), 'Saga Test Item (Out of Stock)', 'Deterministic sentinel product with zero inventory, for saga rejection testing', 19.99, 'test-fixtures');
INSERT INTO products (id, name, description, price, category) VALUES (HEXTORAW('77777777777777777777777777777777'), 'Saga Test Item (Ships Never)', 'Deterministic sentinel product that always fails fulfillment, for saga compensation testing', 24.99, 'test-fixtures');
