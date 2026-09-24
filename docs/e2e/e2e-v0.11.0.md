# E2E — orders-v0.11.0: Saga Orchestration

Reproduces the saga milestone: a durable orchestrated purchase process
(reserve inventory → ship → complete) with idempotent step commands/events,
restart recovery, and inventory-release compensation when fulfillment fails
after a successful reservation.

Test-first policy: the saga state machine was implemented against
`OrderSagaUseCaseTest` (9 tests) written first, covering the mandated edge
cases — duplicate steps, fulfillment failure after reservation, replay
idempotency, and restart recovery.

| Component | Role | Where |
|---|---|---|
| saga-orchestrator-service | Durable saga state machine + command outbox | minikube, NodePort 30087 |
| inventory-service | Reserve/release with `reservation_ledger` idempotency | minikube |
| fulfillment-service | Ship with `fulfillment_ledger` idempotency + failure sentinel | minikube |
| Oracle 23ai Free | `saga_process`, ledgers, outboxes | Docker Compose |
| Kafka | Commands (`*-commands`) and replies (`*-events`) | Docker Compose (host) |

## Saga state machine

```
STARTED ──inventory.reserved──▶ INVENTORY_RESERVED ──fulfillment.shipped──▶ COMPLETED
   │                                    │
   └──inventory.reservation.rejected    └──fulfillment.failed──▶ COMPENSATED
       ▼                                                        (inventory released)
   REJECTED
```

- Every transition is persisted in `saga_process` in the same transaction as
  the outgoing command's outbox row (transactional outbox).
- Handlers reload the durable row and act only when the event matches the
  current state; duplicate/late events are logged no-ops.
- `recoverPending()` re-issues the pending command for non-terminal sagas on
  startup and every 30s; participants deduplicate by sagaId.

## Pre-conditions

- Minikube running, release `demo` deployed with inventory, fulfillment, and
  saga pods Ready:

  ```powershell
  kubectl get pods -n order-platform
  # expect: demo-inventory, demo-fulfillment, demo-saga Running/Ready
  ```

- Host infra running **with the K8s-mode env vars** (same shell):

  ```powershell
  $env:PROMETHEUS_CONFIG='prometheus-k8s.yml'
  $env:KAFKA_EXTERNAL_ADVERTISED_HOST='host.minikube.internal'
  docker compose up -d oracle kafka redis prometheus grafana otel-collector jaeger
  ```

  Without `KAFKA_EXTERNAL_ADVERTISED_HOST` the broker advertises
  `localhost:9092`; pods bootstrap fine but every produce/consume dead-ends
  on the pod's own localhost and sagas stay `STARTED` forever.

- Port-forwards (Windows host cannot route to the minikube IP):

  ```powershell
  kubectl port-forward -n order-platform svc/demo-auth 30084:8080
  kubectl port-forward -n order-platform svc/demo-saga 30087:8080
  ```

## Steps

### 1. Login and start a saga (happy path)

```powershell
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:30084/auth/login' `
  -ContentType 'application/json' -Body '{"username":"alice","password":"password"}'
# expect: accessToken

$orderId = [guid]::NewGuid().ToString()
$body = "{`"orderId`":`"$orderId`",`"lines`":{`"22222222-2222-2222-2222-222222222222`":3}}"
$r = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:30087/api/v1/sagas' `
  -ContentType 'application/json' -Body $body `
  -Headers @{ Authorization = "Bearer $($r.accessToken)" }
# expect: 201 with sagaId
```

### 2. Verify the happy path in Oracle

```powershell
"SELECT RAWTOHEX(saga_id), status FROM saga_process WHERE saga_id = HEXTORAW('<SAGA_ID_HEX>');
SELECT RAWTOHEX(saga_id), status FROM reservation_ledger WHERE saga_id = HEXTORAW('<SAGA_ID_HEX>');
SELECT RAWTOHEX(saga_id), status FROM fulfillment_ledger WHERE saga_id = HEXTORAW('<SAGA_ID_HEX>');
SELECT RAWTOHEX(product_id), available_quantity, reserved_quantity FROM stock
  WHERE product_id = HEXTORAW('22222222222222222222222222222222');
EXIT;" | docker exec -i order-processing-platform-oracle-1 sh -c "cat > /tmp/q.sql"
docker exec order-processing-platform-oracle-1 sqlplus -s orders/orders@localhost/FREEPDB1 @/tmp/q.sql
```

Expect: saga `COMPLETED`, reservation `RESERVED`, fulfillment `SHIPPED`,
stock decremented (1000 → 997 available, 3 reserved).

### 3. Inventory rejection path

Product `66666666-...` is seeded with 0 stock:

```powershell
# start a saga with lines {"66666666-6666-6666-6666-666666666666":1}
```

Expect: saga `REJECTED` with reason `insufficient stock for product ...`,
no fulfillment ledger row.

### 4. Fulfillment failure → compensation

Product `77777777-...` is the deterministic failure sentinel
(`ShipOrderUseCase.ALWAYS_FAILS_PRODUCT_ID`):

```powershell
# start a saga with lines {"77777777-7777-7777-7777-777777777777":2}
```

Expect: fulfillment `FAILED` (carrier rejected), reservation `RELEASED`
(stock back to 500/0), saga `COMPENSATED` with the carrier reason.

### 5. Idempotency / replay

Re-issue the same command (restart recovery does this automatically):
ledger row counts per sagaId stay at exactly 1 in both
`reservation_ledger` and `fulfillment_ledger`; stock is decremented once.

### 6. Restart recovery

Kill a saga mid-flight (e.g. stop Kafka so replies are lost), then restart
saga-orchestrator. `recoverPending()` re-issues the pending command and the
saga reaches a terminal state. Verified: two sagas stranded in `STARTED`
from a broker outage were driven to `REJECTED` by the startup recovery.

## Results (verified run, 2026-09-24)

| Scenario | Saga status | Participant evidence |
|---|---|---|
| Happy path (product 2222, qty 3) | `COMPLETED` | reservation `RESERVED`, fulfillment `SHIPPED`, stock 997/3 |
| Inventory rejection (product 6666) | `REJECTED` | ledger `REJECTED` + reason, no shipment |
| Fulfillment failure (product 7777) | `COMPENSATED` | fulfillment `FAILED`, reservation `RELEASED`, stock restored 500/0 |
| Replay/idempotency | unchanged | no duplicate ledger rows per sagaId |
| Restart recovery | terminal | 2 stale `STARTED` sagas driven to `REJECTED` on boot |

Final `saga_process` distribution: 3 `COMPLETED`, 3 `REJECTED`,
1 `COMPENSATED`, 0 `STARTED`.

## Regression

- Full reactor `mvn verify`: BUILD SUCCESS, 71 tests across 10 modules.
- PIT: saga-orchestrator 17/19 killed (89%, test strength 94%),
  inventory 16/17 (94%), fulfillment 11/12 (92%) — all above the 70
  threshold.

## Gotchas

- **Kafka advertised listener**: compose must run with
  `KAFKA_EXTERNAL_ADVERTISED_HOST=host.minikube.internal` or sagas hang in
  `STARTED` (see helm README Gotchas).
- **Participant reply envelopes**: inventory/fulfillment outboxes publish
  `{type, payload}` envelopes; the saga listener dispatches on the explicit
  `type` discriminator (topic-based inference remains only as a legacy
  fallback). Without the discriminator a rejection is indistinguishable
  from a reservation and the saga completes wrongly.
- **Node load**: 11 JVMs on an 8GB minikube node cause boot storms and
  apiserver timeouts; scale `demo-order` to 1 and idle services to 0 while
  running the saga E2E.
- **minikube container restart changes the apiserver port**: run
  `minikube update-context` (and `minikube start` if Stopped).

## Interview talking points

- The orchestrator persists state + command in one transaction (outbox), so
  a crash between decision and publish is healed by the relay, and a crash
  after publish but before reply is healed by `recoverPending()`.
- Compensation is a single `inventory.release` command keyed by sagaId;
  duplicate failure events re-enter the branch only from
  `INVENTORY_RESERVED`, so the release is sent exactly once.
- Payment refund/reverse compensation is not in scope yet — payment-service
  has no reversal capability; the saga currently coordinates inventory and
  fulfillment only.
