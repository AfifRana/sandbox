# Personal Notes — Order Management Platform

Reference notes for the portfolio build. Domain: e-commerce order processing.

## Required backend-concept coverage

This portfolio must fully demonstrate the following concepts. Completion means
the implementation, automated tests, E2E reproduction steps, and documentation
exist; configuring a library without proving its behavior is not sufficient.

| Concept | Project implementation and proof |
|---|---|
| Synchronous programming | REST request/response flows through the gateway; validation, Problem Details errors, database transactions, and synchronous catalog/payment interactions are covered by unit, API, and E2E tests. |
| Asynchronous programming | Transactional outbox relays publish Kafka events after commits; payment/order/notification consumers use at-least-once delivery with idempotent inbox or Redis deduplication. Duplicate delivery is E2E tested. |
| Design patterns | Hexagonal ports/adapters and repository ports isolate business logic; payment methods use Strategy dispatch; outbox/inbox, cache-aside, and circuit-breaker/retry policies solve specific distributed-system concerns. |
| Saga pattern | Required completion: an orchestrated, durable order process coordinates inventory reservation, payment, and fulfillment. Each command/event is idempotent; failures compensate completed work by releasing inventory and refunding/reversing payment where appropriate. E2E tests must cover success, rejection, retry, timeout, and failure after payment. The current outbox/inbox event flow is not a Saga because it has no process coordinator or compensations. |
| Containerization | Each service has a multi-stage, non-root image; Docker Compose supplies a reproducible full-stack local runtime. |
| Orchestration | Helm deploys the platform to Kubernetes with resource limits, startup/liveness/readiness probes, HPA, and a full gateway-based cluster E2E flow. |
| CI/CD | CI builds, tests, produces coverage artifacts, and builds images. Required completion: immutable GHCR publication plus a protected, approved staging Helm deployment using pinned image digests/tags, smoke tests, and documented rollback. |
| Multithreading and concurrency | Virtual threads, scheduled outbox relay work, and connection-pool limits establish runtime foundations. Required completion: bounded Kafka listener concurrency, partition-order behavior, race-safe idempotency, coordinated parallel-request tests, and concurrency metrics. |
| Extreme concurrency / flash sale | Required completion: limited-stock inventory reservation with one authoritative atomic decrement/conditional update or equivalent transactionally safe reservation, idempotency keys, reservation expiry/release, oversell prevention, contention controls, and a high-parallelism E2E/load test proving successful reservations never exceed stock. |
| Caching | product-service uses Redis cache-aside reads, TTL, evict-on-write invalidation, cache miss/hit E2E tests, and a documented fail-open Redis-outage policy. |

## Authentication and authorization coverage

| Concept | Current implementation |
|---|---|
| Credential authentication | `auth-service` accepts demo username/password credentials and issues a short-lived access token. Users are in memory for the portfolio demo. |
| JWT | Tokens carry standard issuer, subject, issued-at, expiry, and unique `jti` claims, plus a custom `roles` claim. |
| Public/private-key cryptography | `auth-service` generates an RSA-2048 key pair and signs JWTs with the private key using RS256. The private key never leaves auth-service; resource servers verify signatures with the public key. |
| JWKS | `GET /oauth2/jwks` exposes the public RSA key in standard JSON Web Key Set format with `kid`, `kty`, `n`, `e`, `alg`, and signature-use metadata. |
| OAuth2 resource server | The gateway and order/product/payment services use OAuth2 resource-server JWT validation. The gateway validates at the edge; services validate again for defense in depth. |
| Role-based access control | The `roles` claim maps to `ROLE_*` authorities. Customer-only payments/order creation, customer-or-admin order reads, admin-only product writes, and public catalog reads are enforced and web-layer tested. |
| Stateless API security | CSRF is disabled for the bearer-token APIs; protected routes require a valid JWT and unauthenticated/unauthorized paths are tested. |
| Not implemented | This is not a complete OAuth2/OIDC authorization server: no authorization-code flow, PKCE, refresh tokens, client registration, consent, discovery, user-info endpoint, token revocation/introspection, persistent keys, or key rotation. |

## Target role checklist → what to build

| Requirement | Deliverable |
|---|---|
| Java & Spring Boot | Spring Boot 3.x, Java 21 (records, virtual threads) |
| REST API and security | OpenAPI 3, Bean Validation, pagination, RFC 7807 problem+json errors; RS256 JWTs, JWKS, OAuth2 resource-server validation, RBAC, defense in depth |
| Oracle + SQL optimization | Indexed/partitioned schema, EXPLAIN PLAN before/after writeup, HikariCP tuning, batch inserts |
| Microservices | 4 services, service discovery, config, Resilience4j (circuit breaker, retry, bulkhead) |
| JUnit/Mockito | Unit + integration tests (Testcontainers), JaCoCo ≥ 80%, PIT mutation testing |
| Design Patterns & Clean Code | Strategy (payments), Factory, Observer (events), Hexagonal architecture, ArchUnit layering tests |
| Docker & CI/CD | Multi-stage Dockerfile, docker-compose, GitHub Actions build/test/coverage/image, GHCR publication, protected staging Helm deployment, smoke test, rollback |
| Messaging and distributed transactions | Kafka with **outbox pattern** for reliable event publishing; orchestrated Saga with explicit compensations for long-running order fulfillment |
| Redis (bonus) | Catalog caching, rate limiting |
| Kubernetes/Cloud (bonus) | Helm chart, probes, HPA, Minikube/kind or free-tier cloud |

## Architecture

```mermaid
graph LR
    Client --> GW[API Gateway]
    GW --> ORD[order-service]
    GW --> PROD[product-service]
    ORD --> K[(Kafka)]
    K --> PAY[payment-service]
    K --> NOTIF[notification-service]
    ORD --> O[(Oracle)]
    PROD --> O
    PAY --> R[(Redis)]
```

## The differentiators (what actually wins the interview)

1. **README as a case study** — architecture diagram, ADRs, tradeoffs (why Kafka over RabbitMQ, why outbox pattern).
2. **Performance section** — k6/Gatling load test; SQL optimization before/after with real numbers.
3. **Observability** — structured logs, Micrometer + Prometheus + Grafana, OpenTelemetry tracing.
4. **Security** — JWT/OAuth2 resource server, secrets via env vars / Vault, never in code.
5. **Zero-downtime design** — Flyway migrations, backward-compatible API versioning, graceful shutdown.

## Build order (2–3 weeks part-time)

1. Core: order-service + product-service + Oracle + Flyway + REST + tests
2. Kafka event flow + payment/notification services + outbox pattern
3. Inventory/payment/fulfillment Saga with compensations and failure recovery
4. Dockerize + docker-compose + CI/CD delivery pipeline
5. Concurrency hardening and a limited-stock flash-sale reservation case study
6. Kubernetes + observability
7. Full regression verification, then polish README, diagrams, and ADRs —
   **this is the interview material; spend real time here**

## Cheap bonus wins

- Demo video/GIF in the README
- Live deployment on a free tier
- "Known limitations / next steps" section — self-aware tradeoffs read senior

## Interview talking points to prepare

- Why outbox pattern solves dual-write problem (DB + Kafka consistency)
- Idempotent consumers (dedup keys, exactly-once vs at-least-once)
- Why the current outbox/inbox flow is not a Saga; orchestrator versus
  choreography; compensation versus database rollback; and why the order
  workflow uses an orchestrated Saga
- Flash-sale consistency: why the reservation operation is atomic, how
  idempotency and expiry prevent overselling, and how contention results are
  verified under high parallelism
- RS256/JWKS: private-key signing, public-key verification, key identifiers,
  JWT claims, OAuth2 resource-server validation, and the boundary between this
  implementation and a full OAuth2/OIDC authorization server
- SQL tuning story: slow query → EXPLAIN PLAN → index/partition → measured improvement
- Circuit breaker: when it trips, fallback strategy, half-open recovery
- Hexagonal architecture: ports/adapters, why testability improves
- Virtual threads: when they help vs platform threads in I/O-heavy services
