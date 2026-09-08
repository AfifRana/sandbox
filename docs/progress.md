# Project Progress

Track of what's done and what's next. Update as you go.

## 🏷️ Releases / tags

| Tag | What it marks |
|---|---|
| `orders-v0.1.0` | Microservices scaffold, E2E verified: REST → Oracle → outbox → Kafka → consumers |
| `orders-v0.2.0` | product-service with Oracle persistence + Redis cache-aside, E2E verified (cache miss/hit, TTL, evict-on-write) |
| `orders-v0.3.0` | payment-service with Strategy pattern + payment.paid event flow, E2E verified (order CREATED→PAID, idempotency, 402 decline) |
| `orders-v0.4.0` | JWT/OAuth2 security: auth-service (RS256 + JWKS), API Gateway, resource servers, E2E verified (role matrix, defense in depth) |
| `orders-v0.5.0` | Idempotent consumers: transactional inbox (order-service), Redis SETNX dedupe (notification-service), E2E verified (duplicate replay → single processing) |
| `orders-v0.6.0` | Resilience4j retry + circuit breaker on catalog calls, authoritative pricing, fail-open/fail-closed, E2E verified (circuit OPEN → fail-fast → recovery) |
| `orders-v0.7.0` | Observability: Prometheus metrics (all services), provisioned Grafana dashboard, Micrometer Tracing + OTel collector + Jaeger, E2E verified (7 targets up, 3-service trace) |

Tags point at the branch tip when the milestone was verified and documented — `git checkout <tag>` shows a progress.md with that milestone marked complete. Use `git show <tag>` to see a tag's commit. Tags are local until pushed (`git push origin orders-v0.2.0`). E2E reproduction steps per milestone live in [docs/e2e/](e2e/README.md).

## ✅ Done

- [x] Strategy notes & requirement mapping (`docs/notes.md`)
- [x] Multi-module Maven setup (Java 21, Spring Boot 3.3)
- [x] order-service: hexagonal architecture (domain / application / adapters)
- [x] order-service: REST API with Bean Validation + RFC 7807 error handling
- [x] order-service: JPA persistence + Oracle schema (Flyway V1 migration)
- [x] order-service: SQL tuning foundations (indexed FKs, partial index on outbox, NUMBER(12,2) money)
- [x] order-service: transactional outbox → Kafka relay
- [x] order-service: virtual threads enabled, HikariCP pool config
- [x] Tests: JUnit 5 + Mockito unit tests (CreateOrderUseCaseTest)
- [x] Tests: ArchUnit hexagonal architecture enforcement
- [x] JaCoCo coverage plugin configured
- [x] product-service: hexagonal architecture with Oracle persistence + Redis cache-aside
- [x] product-service: evict-on-write cache invalidation, TTL 10 min, fail-open on Redis outage
- [x] product-service: Flyway migration + seed data, per-service history tables (shared schema)
- [x] product-service: REST API (GET/POST/PUT) with RFC 7807 errors, 6/6 tests green
- [x] End-to-end verified: cache miss → DB → cache hit (TTL 599s), PUT → eviction → repopulate
- [x] Parent POM: `-parameters` compiler flag (Spring 6.1+ argument name discovery)
- [x] `.dockerignore` (stale target/ classes no longer leak into images)
- [x] Compose port assignments: order 8080, product 8081, payment 8082, notification 8083
- [x] Tag `orders-v0.1.0` (E2E-verified scaffold, commit e54757d)
- [x] payment-service: Strategy pattern (card / wallet / bank transfer) via EnumMap dispatch
- [x] payment-service: transactional outbox → `payment.paid` on Kafka
- [x] payment-service: idempotency — one payment per order (UNIQUE constraint + use-case check)
- [x] payment-service: declined charges → 402 Payment Required (RFC 7807)
- [x] order-service: consumes `payment.paid` → status CREATED→PAID (idempotent transition)
- [x] order-service: GET /api/v1/orders/{id} endpoint
- [x] E2E verified: order → payment → PAID; duplicate payment no-op; card decline 402; bank transfer PENDING; outbox drained
- [x] payment-service skeleton (Kafka consumer with idempotency notes)
- [x] notification-service skeleton (Kafka consumer)
- [x] Multi-stage Dockerfile (non-root user, MaxRAMPercentage)
- [x] docker-compose: Oracle 23ai Free, Kafka (KRaft), Redis, all services
- [x] GitHub Actions CI: build → test → coverage artifact → Docker image
- [x] README with architecture diagram, design decisions, quick start
- [x] Build verified: `mvn verify` → BUILD SUCCESS, 4/4 tests green
- [x] docker-compose: named volume for Oracle data
- [x] End-to-end verified via `docker compose up`: REST → Oracle → outbox → Kafka → payment & notification consumers
- [x] auth-service: RS256 JWT issuing (Nimbus JOSE), custom `roles` claim, unique `jti`, 1h TTL
- [x] auth-service: JWKS endpoint (`GET /oauth2/jwks`) — public key only, private key never leaves the service
- [x] auth-service: demo users (alice/CUSTOMER, bob/ADMIN, carol/both), bad creds → 401 RFC 7807
- [x] api-gateway: Spring Cloud Gateway routes (auth, orders, products, payments), edge JWT validation, CORS
- [x] Resource servers: order/product/payment validate JWTs independently (defense in depth — bypassing the gateway still 401s)
- [x] Role rules: order POST=CUSTOMER, order read=CUSTOMER/ADMIN, product GET=public, product writes=ADMIN, payments=CUSTOMER only
- [x] `roles` claim → ROLE_* authorities via JwtAuthenticationConverter in each service
- [x] Security tests: @WebMvcTest slices with jwt() postprocessor — 401/403/pass matrix (9 tests)
- [x] E2E verified: login → token → role matrix (401/403/201) → order → payment → PAID, all through the gateway on :9000
- [x] Idempotent consumers: `processed_events` transactional inbox in order-service (claim + state change in one DB transaction)
- [x] Idempotent consumers: Redis SETNX dedupe in notification-service (TTL 1 day, fail-open on Redis outage)
- [x] PaymentEventListener: Jackson parsing of paymentId + orderId (was manual string indexOf)
- [x] Tests: HandlePaymentPaidEventTest (2), NotificationDeduplicatorTest (3) — 15/15 order, 3/3 notification
- [x] E2E verified: 3 duplicate payment.paid replays → 1 inbox row, order stays PAID; duplicate order.created → 1 notification, Redis key present
- [x] Resilience4j: retry (3 attempts, 200ms) + circuit breaker (50% failure rate, 10-call window, min 5, 10s open wait) on order→product catalog calls
- [x] Authoritative pricing: order-service fetches catalog price server-side, client-supplied unitPrice no longer trusted
- [x] Fail-open on catalog outage (client price + WARN) vs fail-closed on unknown product (400) — policy lives in the use case, not the adapter
- [x] Actuator: `/actuator/circuitbreakers` endpoint (ADMIN-only), circuit-breaker health indicator
- [x] Tests: CreateOrderUseCaseTest (5), ProductCatalogClientCircuitBreakerTest (1) — 16/16 order-service green
- [x] E2E verified: wrong client price overridden with 99.99; unknown product 400; outage fail-open 88.88; circuit OPEN with notPermittedCalls; recovery to CLOSED
- [x] Observability: micrometer-registry-prometheus on all 6 services, `/actuator/prometheus` exposed (permitAll — internal Docker network scrape only)
- [x] Prometheus: scrapes all 6 services + itself on the internal network (5s interval), UI on :9090
- [x] Grafana: provisioned datasource + 8-panel dashboard (HTTP rate/p95, JVM heap, CB state + calls, Kafka lag, CPU, HikariCP), UI on :3000
- [x] Tracing: Micrometer Tracing (OTel bridge) + OTLP exporter on all 6 services, 100% sampling
- [x] OTel Collector (batch → Jaeger) + Jaeger UI on :16686
- [x] Trace propagation fix: ProductCatalogClient now injects the auto-configured RestClient.Builder (raw builder is not instrumented — no traceparent header)
- [x] E2E verified: 7/7 Prometheus targets up, CB state metric, POST 201 counters, Grafana panels live, single trace ID across api-gateway → order-service → product-service

## 🔜 Next up (priority order)

- [ ] Kubernetes: Helm chart, liveness/readiness probes, HPA (Minikube/kind)
  — **chart written** (`deploy/helm/order-platform/`), `helm lint`/`helm template`
  clean, but **not yet deployed**: Docker Desktop's Kubernetes failed to
  bootstrap on the authoring device (control-plane never answered, no
  auto-retry; a disable/re-enable cycle didn't fix it either). Picking this
  up on a new device — see [deploy/helm/order-platform/README.md](../deploy/helm/order-platform/README.md#resume--first-deploy-on-a-fresh-device)
  "Resume / first deploy" for exact steps. Do not tag `orders-v0.8.0` until
  an E2E order-creation run through the chart's NodePorts succeeds.
- [ ] k6/Gatling load test + SQL EXPLAIN PLAN before/after case study
- [ ] PIT mutation testing run
- [ ] ADRs (why Kafka over RabbitMQ, why outbox pattern)
- [ ] Demo GIF/video for README
- [ ] "Known limitations / next steps" section refresh before publishing

## 📌 Interview prep (from notes.md)

- [ ] Outbox pattern & dual-write problem — rehearse the story
- [ ] SQL tuning story: slow query → EXPLAIN PLAN → index → measured gain
- [ ] Circuit breaker behavior: trip conditions, fallbacks, half-open recovery — implemented in v0.6.0, rehearse the story
- [ ] Virtual threads vs platform threads for I/O-heavy services
- [x] At-least-once vs exactly-once delivery, idempotent consumer design — inbox + Redis SETNX implemented in v0.5.0
