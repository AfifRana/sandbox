# E2E — orders-v0.4.0: JWT/OAuth2 Security

Verified 2026-09-05. Scope: auth-service (RS256 + JWKS), api-gateway (edge
validation + routing), order/product/payment as OAuth2 resource servers.

## Pre-conditions

- Docker Desktop running
- Full stack built and started:

```bash
docker compose up --build -d
```

- Wait until all services are up (first start pulls images + builds jars,
  ~2-3 min):

```bash
docker compose ps          # all "Up", oracle "Up (healthy)"
curl http://localhost:9000/actuator/health   # {"status":"UP"}
```

- Ports: gateway **9000**, auth 8084, order 8080, product 8081, payment 8082
- Demo users (password `password` for all): `alice` = CUSTOMER, `bob` = ADMIN,
  `carol` = CUSTOMER + ADMIN

## 1. Login and token issuing

```bash
# alice (CUSTOMER) — expect 200 with {"accessToken":"eyJ..."}
curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}'
```

```bash
# Capture tokens for later steps
ALICE=$(curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
BOB=$(curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"password"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
```

```bash
# Wrong password — expect 401 (RFC 7807 problem+json)
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"wrong"}'
```

```bash
# Inspect the token payload (roles claim, issuer, jti, exp)
echo $ALICE | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null; echo
# {"iss":"http://auth-service:8080","sub":"alice",...,"roles":["CUSTOMER"]}
```

## 2. JWKS endpoint (public key distribution)

```bash
# Expect 200: {"keys":[{"kty":"RSA","e":"AQAB","kid":"...","alg":"RS256","n":"..."}]}
curl -s http://localhost:9000/oauth2/jwks
```

The private key never leaves auth-service; resource servers fetch this
document to verify token signatures.

## 3. Role matrix through the gateway

```bash
# No token — expect 401
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:9000/api/v1/orders/00000000-0000-0000-0000-000000000000

# alice (CUSTOMER) creates an order — expect 201, status "CREATED"
curl -s -X POST http://localhost:9000/api/v1/orders \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{
    "customerId": "11111111-1111-1111-1111-111111111111",
    "lines": [{"productId": "22222222-2222-2222-2222-222222222222", "quantity": 2, "unitPrice": 19.99}]
  }'
# Save the id from the response:
ORDER_ID=<paste-id>

# bob (ADMIN) creates an order — expect 403 (POST is CUSTOMER-only)
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:9000/api/v1/orders \
  -H "Authorization: Bearer $BOB" -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","lines":[]}'

# bob reads alice's order — expect 200 (reads allow CUSTOMER or ADMIN)
curl -s -H "Authorization: Bearer $BOB" http://localhost:9000/api/v1/orders/$ORDER_ID

# Product catalog read, no token — expect 200 (public browsing)
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:9000/api/v1/products/22222222-2222-2222-2222-222222222222

# bob (ADMIN) writes a product — expect 201
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:9000/api/v1/products \
  -H "Authorization: Bearer $BOB" -H "Content-Type: application/json" \
  -d '{"name":"Test Widget","price":9.99}'

# alice (CUSTOMER) writes a product — expect 403
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:9000/api/v1/products \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{"name":"Nope","price":1.00}'
```

## 4. Secured business flow: order → payment → PAID

```bash
# alice pays for her order — expect 201, status "COMPLETED"
curl -s -X POST http://localhost:9000/api/v1/payments \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$ORDER_ID\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":39.98,\"method\":\"CARD\"}"

# bob (ADMIN) cannot pay — expect 403 (payments are CUSTOMER-only)
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:9000/api/v1/payments \
  -H "Authorization: Bearer $BOB" -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$ORDER_ID\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":39.98,\"method\":\"CARD\"}"

# Wait for the payment.paid Kafka event, then read the order — expect "PAID"
sleep 8
curl -s -H "Authorization: Bearer $ALICE" http://localhost:9000/api/v1/orders/$ORDER_ID
```

## 5. Defense in depth (bypass the gateway)

Each service re-validates tokens independently, so hitting a service directly
must still fail without a token:

```bash
# Direct to order-service — expect 401
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:8080/api/v1/orders/00000000-0000-0000-0000-000000000000

# Direct to payment-service — expect 401
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8082/api/v1/payments

# Health endpoints stay public — expect {"status":"UP"}
curl -s http://localhost:8080/actuator/health
```

## Post-conditions

- `alice` token has `roles: ["CUSTOMER"]`; `bob` has `["ADMIN"]`
- Order created by alice transitions CREATED → PAID after her payment
- Every protected endpoint returns 401 without a token, 403 with the wrong
  role — both via the gateway (9000) and directly (8080/8081/8082)
- `docker compose logs order-service` shows the `payment.paid` event consumed
