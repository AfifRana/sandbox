CREATE TABLE products (
    id          RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    name        VARCHAR2(200) NOT NULL,
    description VARCHAR2(2000),
    price       NUMBER(12,2) NOT NULL CHECK (price >= 0),
    category    VARCHAR2(100)
);

CREATE INDEX idx_products_category ON products (category);

-- Seed data for demos and load testing (RAW(16) = UUID bytes; HEXTORAW strips nothing, so pass pure hex)
INSERT INTO products (id, name, description, price, category) VALUES (HEXTORAW('22222222222222222222222222222222'), 'Mechanical Keyboard', 'Hot-swappable switches, PBT keycaps', 89.99, 'peripherals');
INSERT INTO products (id, name, description, price, category) VALUES (HEXTORAW('33333333333333333333333333333333'), 'USB-C Docking Station', 'Dual 4K display support, 100W PD', 149.50, 'peripherals');
INSERT INTO products (id, name, description, price, category) VALUES (HEXTORAW('44444444444444444444444444444444'), '27-inch 4K Monitor', 'IPS panel, 99% sRGB, height adjustable', 329.00, 'displays');
INSERT INTO products (id, name, description, price, category) VALUES (HEXTORAW('55555555555555555555555555555555'), 'Wireless Mouse', 'Ergonomic, 8K polling rate', 59.99, 'peripherals');
