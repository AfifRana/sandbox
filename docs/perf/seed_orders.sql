-- Seed orders for performance tests (Oracle)
-- Usage with sqlplus:
--   sqlplus -s orders/orders@FREEPDB1 @seed_orders.sql
-- The defaults create a selective 60,000-row distribution for the case study.

PROMPT Seeding orders for performance tests

DEFINE TARGET_COUNT = 10000
DEFINE OTHER_COUNT = 50000
DEFINE CUSTOMER_UUID = 11111111-1111-1111-1111-111111111111
DEFINE OTHER_CUSTOMER_UUID = aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
DEFINE PRODUCT_UUID = 22222222-2222-2222-2222-222222222222
DEFINE UNIT_PRICE = 89.99

DECLARE
  v_order_id RAW(16);
  v_target_count NUMBER := &TARGET_COUNT;
  v_other_count NUMBER := &OTHER_COUNT;
BEGIN
  FOR i IN 1..v_target_count LOOP
    v_order_id := SYS_GUID();
    INSERT INTO orders (id, customer_id, status, total_amount, created_at)
    VALUES (
      v_order_id,
      HEXTORAW(REPLACE('&CUSTOMER_UUID','-','')),
      'CREATED',
      &UNIT_PRICE,
      SYSTIMESTAMP - NUMTODSINTERVAL(i, 'MINUTE')
    );

    INSERT INTO order_lines (order_id, product_id, quantity, unit_price)
    VALUES (
      v_order_id,
      HEXTORAW(REPLACE('&PRODUCT_UUID','-','')),
      1,
      &UNIT_PRICE
    );
  END LOOP;

  FOR i IN 1..v_other_count LOOP
    v_order_id := SYS_GUID();
    INSERT INTO orders (id, customer_id, status, total_amount, created_at)
    VALUES (
      v_order_id,
      HEXTORAW(REPLACE('&OTHER_CUSTOMER_UUID','-','')),
      'CREATED',
      &UNIT_PRICE,
      SYSTIMESTAMP - NUMTODSINTERVAL(i, 'SECOND')
    );

    INSERT INTO order_lines (order_id, product_id, quantity, unit_price)
    VALUES (
      v_order_id,
      HEXTORAW(REPLACE('&PRODUCT_UUID','-','')),
      1,
      &UNIT_PRICE
    );
  END LOOP;
  COMMIT;
END;
/

PROMPT Done seeding orders. Verify with:
PROMPT SELECT COUNT(*) FROM ORDERS WHERE CUSTOMER_ID = HEXTORAW(REPLACE('&CUSTOMER_UUID','-',''));
