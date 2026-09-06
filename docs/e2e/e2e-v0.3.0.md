# E2E — orders-v0.3.0: Payment Service (Strategy Pattern)

Verified at tag `orders-v0.3.0`. Scope: payment-service with card/wallet/bank
strategies, idempotent payments, `payment.paid` event → order CREATED→PAID.

> **Note:** at this tag there was **no authentication** — no token needed.
> On later tags the same endpoints require a Bearer token (see
> [e2e-v0.4.0.md](e2e-v0.4.0.md)); the business expectations are unchanged.

## Pre-conditions

```bash
git checkout orders-v0.3.0
docker compose up --build -d
docker compose ps        # all Up, oracle healthy
```

## 1. Create an order

```bash
# Expect 201, status "CREATED"
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "11111111-1111-1111-1111-111111111111",
    "lines": [{"productId": "22222222-2222-2222-2222-222222222222", "quantity": 1, "unitPrice": 100.00}]
  }'
ORDER_ID=<paste-id>
```

## 2. Pay with a card (happy path)

```bash
# Expect 201, status "COMPLETED", method "CARD"
curl -s -X POST http://localhost:8082/api/v1/payments \
  -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$ORDER_ID\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":100.00,\"method\":\"CARD\"}"
```

## 3. Idempotency — duplicate payment is a no-op

```bash
# Same request again — expect 200/201 with the ORIGINAL payment (no double charge)
curl -s -X POST http://localhost:8082/api/v1/payments \
  -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$ORDER_ID\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":100.00,\"method\":\"CARD\"}"
```

## 4. Redis cache-aside (carried over from v0.2.0)

```bash
# First read — cache miss, loads from Oracle, expect 200 + product JSON
curl -s http://localhost:8081/api/v1/products/22222222-2222-2222-2222-222222222222

# Key now cached with 10-minute TTL (verify inside the container):
docker compose exec redis redis-cli TTL product:22222222-2222-2222-2222-222222222222
# Expect ~600

# Update the product — cache is evicted (evict-on-write)
curl -s -X PUT http://localhost:8081/api/v1/products/22222222-2222-2222-2222-222222222222 \
  -H "Content-Type: application/json" \
  -d '{"name":"Mechanical Keyboard","description":"Updated","price":84.99,"category":"peripherals"}'

docker compose exec redis redis-cli EXISTS product:22222222-2222-2222-2222-222222222222
# Expect 0 (evicted); next GET repopulates it
```

## 5. Card decline — 402 Payment Required

```bash
# New order first
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","lines":[{"productId":"22222222-2222-2222-2222-222222222222","quantity":1,"unitPrice":6000.00}]}'
ORDER_ID2=<paste-id>

# Card limit is 5000.00 — expect 402 (RFC 7807 problem+json)
curl -s -X POST http://localhost:8082/api/v1/payments \
  -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$ORDER_ID2\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":6000.00,\"method\":\"CARD\"}"
```

## 6. Bank transfer — asynchronous, PENDING

```bash
# Expect 201, status "PENDING" (settles out of band)
curl -s -X POST http://localhost:8082/api/v1/payments \
  -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$ORDER_ID2\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":6000.00,\"method\":\"BANK_TRANSFER\"}"
```

## 7. Order transitions to PAID via Kafka

```bash
sleep 8
# Expect status "PAID"
curl -s http://localhost:8080/api/v1/orders/$ORDER_ID
```

## Post-conditions

- Paid order shows `PAID`; bank-transfer order stays `PENDING`
- Duplicate payment returns the original payment (UNIQUE constraint on order)
- `docker compose logs payment-service` shows the outbox drained
- `docker compose logs order-service` shows `payment.paid` consumed
