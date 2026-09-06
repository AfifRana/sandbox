# E2E — orders-v0.6.0: Resilience4j Retry + Circuit Breaker

Reproduces the milestone: order-service no longer trusts the client-supplied
`unitPrice`. It calls product-service (`GET /api/v1/products/{id}`) for the
authoritative price, protected by Resilience4j **retry** (transient failures)
and a **circuit breaker** (sustained outages), with two distinct failure
postures:

| Scenario | Behavior | Rationale |
|---|---|---|
| Catalog healthy | Client price **overridden** with catalog price | Server-side data is authoritative |
| Unknown product | **Fail closed** — HTTP 400 | A business 404 is a client error, not an availability issue |
| Catalog unreachable | **Fail open** — order accepted with client price + WARN log | Availability beats perfect pricing; the order can be repriced later |

## Pre-conditions

- Full stack running: `docker compose up -d --build` (9 containers)
- Git Bash / WSL for the shell snippets (token capture uses `sed`)
- `docker compose ps` shows all services `Up`, Oracle `(healthy)`
- Seed product `22222222-2222-2222-2222-222222222222` (Mechanical Keyboard Pro, 99.99)

## Steps

### 1. Login and capture a token

```bash
ALICE=$(curl -s -X POST http://localhost:9000/auth/login -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
```

### 2. Authoritative pricing (catalog healthy)

```bash
curl -s -X POST http://localhost:9000/api/v1/orders \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","lines":[{"productId":"22222222-2222-2222-2222-222222222222","quantity":2,"unitPrice":1.00}]}'
# expect: "unitPrice": 99.99, "totalAmount": 199.98  (client's 1.00 overridden)
```

### 3. Unknown product → fail closed

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:9000/api/v1/orders \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","lines":[{"productId":"99999999-9999-9999-9999-999999999999","quantity":1,"unitPrice":50.00}]}'
# expect: 400
```

### 4. Catalog outage → fail open

```bash
docker compose stop product-service

curl -s -X POST http://localhost:9000/api/v1/orders \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","lines":[{"productId":"22222222-2222-2222-2222-222222222222","quantity":1,"unitPrice":88.88}]}'
# expect: 201, "unitPrice": 88.88  (client price kept — fail open)

docker compose logs order-service 2>&1 | grep -i "catalog" | tail -2
# expect: WARN ... catalog unavailable, keeping client price (fail-open)
```

### 5. Circuit opens and fails fast

```bash
# 5 more order creations while product-service is down (same curl as step 4)
# then inspect the circuit breaker (ADMIN role required):

BOB=$(curl -s -X POST http://localhost:9000/auth/login -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"password"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -s -H "Authorization: Bearer $BOB" http://localhost:8080/actuator/circuitbreakers
# expect: "productCatalog": { "state": "OPEN", "notPermittedCalls": >0 }
#   - failureRate >= 50% over the sliding window (10 calls, min 5)
#   - notPermittedCalls counts requests rejected without touching the network
```

### 6. Recovery: half-open → closed

```bash
docker compose start product-service
sleep 40   # wait out the 10s open-state window AND product-service startup (~25s)

curl -s -X POST http://localhost:9000/api/v1/orders \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","lines":[{"productId":"22222222-2222-2222-2222-222222222222","quantity":1,"unitPrice":1.00}]}'
# expect: "unitPrice": 99.99  (authoritative pricing restored)

curl -s -H "Authorization: Bearer $BOB" http://localhost:8080/actuator/circuitbreakers
# expect: "state": "CLOSED"
```

## Post-conditions

- Orders created while the catalog was healthy carry the catalog price, not the client price
- Unknown products are rejected with 400 (fail closed)
- Orders created during an outage carry the client price and a WARN log (fail open)
- After ~5 consecutive failures the circuit is OPEN and calls fail fast (`notPermittedCalls` > 0)
- After product-service recovers, the circuit transitions OPEN → HALF_OPEN → CLOSED and authoritative pricing resumes

## Gotchas

- **PowerShell + curl.exe**: passing a long JWT inline in `-H "Authorization: Bearer $token"` can get mangled by console encoding and produce a mysterious 403. Use Git Bash, or pass the header via a file: write `Authorization: Bearer <token>` to a file and use `-H "@file"`.
- **`docker compose up -d order-service` restarts its dependencies** (product-service) — after a fail-open test, stop product-service again before tripping the circuit.
- **Wait for product-service to fully start** (~25s) before probing half-open recovery, or the 3 permitted probe calls fail and the circuit re-opens.
- The circuit breaker actuator endpoint requires the ADMIN role (bob); `/actuator/health` stays public.

## Interview talking points

- **Fail-open vs fail-closed**: unknown product is a *business* 404 → reject (fail closed); catalog outage is an *availability* problem → accept with client price and WARN (fail open). The use case distinguishes the two via a `catalogAnswered` flag — "catalog said no" vs "catalog unreachable".
- **Why both retry AND circuit breaker**: retry absorbs transient blips (3 attempts, 200ms backoff); the circuit breaker stops hammering a dead dependency and lets order creation stay fast via fail-open. Retry alone would make every order wait 3×timeout during an outage.
- **Trip conditions**: ≥50% failure rate over a 10-call sliding window with ≥5 calls minimum — small enough to trip quickly in a demo, large enough to avoid tripping on one flaky call.
- **Half-open recovery**: after 10s the circuit admits 3 probe calls; success closes it, failure re-opens. This is why the recovery step waits ~40s.
- **Why the fallback rethrows**: the Resilience4j fallback method converts all failures to `CatalogUnavailableException` and rethrows — the *use case* (not the adapter) decides fail-open vs fail-closed. Keeping policy in the application layer keeps the hexagonal boundary honest.
