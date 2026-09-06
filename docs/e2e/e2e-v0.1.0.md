# E2E — orders-v0.1.0: Microservices Scaffold

Verified at tag `orders-v0.1.0`. Scope: REST → Oracle → transactional outbox →
Kafka → payment & notification consumers.

> **Note:** at this tag there was **no authentication** and payment-service
> was only a skeleton consumer. Later tags supersede this flow (see
> [e2e-v0.3.0.md](e2e-v0.3.0.md) and [e2e-v0.4.0.md](e2e-v0.4.0.md)).

## Pre-conditions

```bash
git checkout orders-v0.1.0
docker compose up --build -d
docker compose ps        # all Up, oracle healthy
```

## 1. Create an order (REST → Oracle)

```bash
# Expect 201 with id, status "CREATED"
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "11111111-1111-1111-1111-111111111111",
    "lines": [{"productId": "22222222-2222-2222-2222-222222222222", "quantity": 2, "unitPrice": 10.00}]
  }'
```

## 2. Read it back

```bash
curl -s http://localhost:8080/api/v1/orders/<id>
# Expect 200, same order
```

## 3. Transactional outbox → Kafka

The order row and its event row were written in one DB transaction; a relay
publishes the event to the `order-events` topic:

```bash
docker compose logs order-service | Select-String outbox     # PowerShell
docker compose logs order-service | grep -i outbox           # bash
```

## 4. Consumers received the event

```bash
docker compose logs payment-service       # consumed order-events
docker compose logs notification-service  # consumed order-events
```

## Post-conditions

- Order persisted in Oracle (Flyway schema `orders`/`order_lines`/`outbox`)
- `order-events` topic received the event; both consumer groups logged it
- Outbox table drained (relay marks rows processed)
