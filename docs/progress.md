# Project Progress

Track of what's done and what's next. Update as you go.

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
- [x] product-service skeleton (Redis config in place)
- [x] payment-service skeleton (Kafka consumer with idempotency notes)
- [x] notification-service skeleton (Kafka consumer)
- [x] Multi-stage Dockerfile (non-root user, MaxRAMPercentage)
- [x] docker-compose: Oracle 23ai Free, Kafka (KRaft), Redis, all services
- [x] GitHub Actions CI: build → test → coverage artifact → Docker image
- [x] README with architecture diagram, design decisions, quick start
- [x] Build verified: `mvn verify` → BUILD SUCCESS, 4/4 tests green

## 🔜 Next up (priority order)

- [ ] Run `docker compose up --build` — verify full stack end-to-end
- [ ] docker-compose: add named volume for Oracle data (survive `docker compose down`)
- [ ] product-service: Oracle persistence + Redis caching (replace placeholder)
- [ ] payment-service: Strategy pattern for payment methods (card / wallet / bank transfer)
- [ ] payment-service: emit `payment.paid` event; order-service consumes it → status PAID
- [ ] Idempotent consumers: dedupe by orderId (Redis or DB table)
- [ ] API Gateway + JWT/OAuth2 resource servers
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
