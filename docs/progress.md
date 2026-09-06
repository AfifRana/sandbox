# Project Progress

Track of what's done and what's next. Update as you go.

## 🏷️ Releases / tags

| Tag | What it marks |
|---|---|
| `orders-v0.1.0` | Microservices scaffold, E2E verified: REST → Oracle → outbox → Kafka → consumers |
| `orders-v0.2.0` | product-service with Oracle persistence + Redis cache-aside, E2E verified (cache miss/hit, TTL, evict-on-write) |
| `orders-v0.3.0` | payment-service with Strategy pattern + payment.paid event flow, E2E verified (order CREATED→PAID, idempotency, 402 decline) |
| `orders-v0.4.0` | JWT/OAuth2 security: auth-service (RS256 + JWKS), API Gateway, resource servers, E2E verified (role matrix, defense in depth) |

Tags point at the branch tip when the milestone was verified and documented — `git checkout <tag>` shows a progress.md with that milestone marked complete. Use `git show <tag>` to see a tag's commit. Tags are local until pushed (`git push origin orders-v0.2.0`).

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

## 🔜 Next up (priority order)

- [ ] Idempotent consumers: dedupe by orderId beyond status checks (Redis or DB table)
- [ ] Resilience4j: circuit breaker + retry on inter-service calls
- [ ] Observability: Micrometer + Prometheus + Grafana, OpenTelemetry tracing
- [ ] Kubernetes: Helm chart, liveness/readiness probes, HPA (Minikube/kind)
- [ ] k6/Gatling load test + SQL EXPLAIN PLAN before/after case study
- [ ] PIT mutation testing run
- [ ] ADRs (why Kafka over RabbitMQ, why outbox pattern)
- [ ] Demo GIF/video for README
- [ ] "Known limitations / next steps" section refresh before publishing

## 📌 Interview prep (from notes.md)

- [ ] Outbox pattern & dual-write problem — rehearse the story
- [ ] SQL tuning story: slow query → EXPLAIN PLAN → index → measured gain
- [ ] Circuit breaker behavior: trip conditions, fallbacks, half-open recovery
- [ ] Virtual threads vs platform threads for I/O-heavy services
- [ ] At-least-once vs exactly-once delivery, idempotent consumer design
