# E2E — orders-v0.5.0: Idempotent Consumers

Reproduces the milestone: **exactly-once processing** on top of at-least-once Kafka delivery, with two complementary dedupe strategies:

| Consumer | Approach | Why |
|---|---|---|
| order-service (`payment.paid`) | **Transactional inbox** (`processed_events` table) | Claim + state change commit atomically in one DB transaction — true exactly-once processing |
| notification-service (`order.created`) | **Redis SETNX** with 1-day TTL | Cheap, TTL-bounded; fail-open on Redis outage (a duplicate notification beats a lost one) |

## Pre-conditions

- Full stack running: `docker compose up -d --build` (9 containers)
- Git Bash / WSL for the shell snippets (token capture uses `sed`)
- `docker compose ps` shows all services `Up`, Oracle `(healthy)`

## Steps

### 1. Login and capture a token

```bash
ALICE=$(curl -s -X POST http://localhost:9000/auth/login -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
```

### 2. Create an order and pay it (the real event)

```bash
ORDER=$(curl -s -X POST http://localhost:9000/api/v1/orders \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","lines":[{"productId":"22222222-2222-2222-2222-222222222222","quantity":1,"unitPrice":99.99}]}')
ORDER_ID=$(echo "$ORDER" | sed 's/.*"id":"\([^"]*\)".*/\1/')

PAY=$(curl -s -X POST http://localhost:9000/api/v1/payments \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$ORDER_ID\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":99.99,\"method\":\"CARD\"}")
PAYMENT_ID=$(echo "$PAY" | sed 's/.*"id":"\([^"]*\)".*/\1/')   # note: response field is "id"
```

Wait ~8s for the outbox relay + consumer, then confirm:

```bash
curl -s -H "Authorization: Bearer $ALICE" http://localhost:9000/api/v1/orders/$ORDER_ID
# expect: "status":"PAID"
```

### 3. Replay the same payment.paid event 3 times (duplicate delivery)

```bash
PAYLOAD="{\"paymentId\":\"$PAYMENT_ID\",\"orderId\":\"$ORDER_ID\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":99.99,\"method\":\"CARD\",\"status\":\"COMPLETED\"}"
for i in 1 2 3; do
  echo "$PAYLOAD" | docker compose exec -T kafka //opt//kafka//bin//kafka-console-producer.sh \
    --bootstrap-server kafka:29092 --topic payment-events
done
```

> Note: in Git Bash on Windows, use `//opt//kafka//bin//...` (double slashes) so MSYS doesn't rewrite the container path to a Windows path.

### 4. Verify exactly-once processing

```bash
sleep 5
curl -s -H "Authorization: Bearer $ALICE" http://localhost:9000/api/v1/orders/$ORDER_ID
# expect: still "status":"PAID" (no error, no state corruption)

PAYMENT_HEX=$(echo "$PAYMENT_ID" | tr -d '-')
docker compose exec -T oracle sqlplus -s orders/orders@localhost/FREEPDB1 <<SQL
SELECT COUNT(*) FROM processed_events WHERE event_id = HEXTORAW('$PAYMENT_HEX');
EXIT;
SQL
# expect: 1  (three deliveries, one inbox row)
```

### 5. Notification Redis dedupe

```bash
ORDER_PAYLOAD="{\"orderId\":\"$ORDER_ID\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"totalAmount\":99.99,\"status\":\"CREATED\"}"
for i in 1 2; do
  echo "$ORDER_PAYLOAD" | docker compose exec -T kafka //opt//kafka//bin//kafka-console-producer.sh \
    --bootstrap-server kafka:29092 --topic order-events
done

sleep 3
docker compose logs notification-service 2>&1 | grep "$ORDER_ID" | grep Notifying | wc -l
# expect: 1  (duplicates suppressed)

docker compose exec redis redis-cli --scan --pattern "notified:*"
# expect: notified:<orderId>:CREATED (TTL 1 day)
```

## Post-conditions

- `processed_events` has exactly one row per (paymentId, consumer group)
- Order status transitions remain correct under duplicate delivery
- Redis holds `notified:<orderId>:<status>` keys with 1-day TTL
- Malformed events are skipped with a WARN log (dead-lettering is a documented limitation)

## Interview talking points

- **Inbox vs status check**: the v0.3.0 status check only guards one specific transition; the inbox dedupes by event identity, so it generalizes to any event type and survives refactors of the state machine
- **Why the claim must be in the same transaction**: if the claim committed separately, a crash between claim and state change would lose the event forever
- **Redis vs DB trade-off**: Redis SETNX is cheaper but TTL-bounded and fail-open; the DB inbox is durable and transactional but costs a write per event. Match the mechanism to the criticality of the side effect
- **Concurrent duplicates**: two instances processing the same event race on INSERT; the loser gets a PK violation and treats it as "already processed"
