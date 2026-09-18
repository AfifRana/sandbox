# Backend Project Ideation

## Purpose

This document is a language-agnostic blueprint for initiating a serious
backend project that demonstrates production-oriented engineering expertise.
It is intentionally independent of a programming language, framework, cloud
provider, database vendor, or messaging product.

The blueprint can be translated into Java, Go, JavaScript/TypeScript, Python,
Rust, C#, or another ecosystem while preserving the same engineering intent.
Technology choices should support the design goals rather than become the
portfolio story themselves.

## Project concept

Build an order-management platform that models a realistic business workflow:

1. A customer authenticates and browses a product catalog.
2. A customer creates an order from one or more products.
3. The platform validates availability and authoritative pricing.
4. A payment is processed through a method-specific strategy.
5. The order transitions through explicit business states.
6. Domain events notify downstream consumers.
7. Notifications and other projections react idempotently.
8. Operators can observe, operate, and recover the platform.

The business domain is intentionally familiar. The differentiator is the
quality of the backend engineering: boundaries, consistency, failure handling,
testability, observability, performance evidence, and operational readiness.

## Expertise the project should showcase

### Domain and application design

- Explicit domain entities, value objects, invariants, and state transitions.
- Use cases that express business policy independently of transport and
  persistence concerns.
- Ports and adapters, modular boundaries, or an equivalent separation of
  responsibilities.
- Strategy or policy abstractions where behavior varies by business option.
- Clear ownership of authoritative data and decisions.

### API design

- Resource-oriented HTTP APIs with consistent naming and status codes.
- Request validation and stable error responses.
- Pagination, filtering, sorting, and bounded query parameters.
- Idempotency for retried commands.
- Correlation identifiers and trace context propagation.
- Versioning or an explicit compatibility policy.

### Data and consistency

- Schema migrations under source control.
- Constraints, indexes, transaction boundaries, and query plans.
- A documented consistency model for every cross-component workflow.
- Transactional outbox or an equivalent reliable event-publication pattern.
- Cache-aside behavior with an explicit invalidation and outage policy.
- Idempotent consumers using durable or expiring deduplication records.

### Distributed systems

- Asynchronous events for integration and decoupling.
- Retry policies with backoff and bounded attempts.
- Timeouts, circuit breakers, bulkheads, and rate limits where appropriate.
- Dead-letter or replay handling for failed messages.
- Duplicate delivery and out-of-order delivery considered in the design.
- A documented choice of message broker and the trade-offs behind it.

### Security

- Authentication and authorization separated conceptually.
- Short-lived access tokens or sessions with a clear validation strategy.
- Role or permission rules enforced at the edge and again at service
  boundaries when defense in depth is required.
- Secrets supplied through configuration or a secret manager, never source
  control.
- Input validation, least privilege, auditability, and safe error messages.

### Quality engineering

- Unit tests for domain and application policies.
- Contract or API tests for service boundaries.
- Integration tests for persistence, messaging, and cache behavior.
- End-to-end tests through the real public entry point.
- Architecture tests that enforce dependency direction.
- Line coverage as a signal, not the sole quality target.
- Mutation testing for selected business-critical modules.
- Repeatable test data and deterministic test setup.

### Performance and operations

- A representative workload rather than an arbitrary benchmark.
- Baseline measurements captured before an optimization.
- Explain plans, query metrics, or equivalent evidence for data-access changes.
- Service-level objectives for latency, errors, and availability.
- Metrics, structured logs, distributed traces, and health probes.
- Dashboards and alerts tied to actionable operational symptoms.
- Container and orchestration support with resource limits and graceful
  shutdown.

## Suggested architecture

Use independently deployable components only where the boundary has a reason to
exist. A modular monolith is acceptable for an initial implementation; the
same modules should be separable later if scale or ownership requires it.

Suggested logical components:

- **Identity**: users, credentials, tokens, roles, and key discovery.
- **Gateway or edge**: routing, cross-cutting policies, and request context.
- **Catalog**: product data, pricing, inventory-facing reads, and caching.
- **Inventory**: authoritative stock, time-bounded reservations, and release
  of expired or compensated reservations.
- **Orders**: order lifecycle, validation, totals, and order queries.
- **Payments**: payment methods, idempotency, and payment state.
- **Fulfillment**: shipment or fulfillment requests and their outcome.
- **Notifications**: asynchronous delivery and consumer deduplication.
- **Platform operations**: metrics, traces, health, deployment, and recovery.

The exact process boundaries are implementation choices. Preserve the logical
responsibilities and their ownership rules.

## Core business model

### Order lifecycle

Define a finite state machine instead of allowing arbitrary status updates.
For example:

```text
CREATED -> PAID -> FULFILLING -> COMPLETED
   |                    |
   +-> CANCELLED        +-> FAILED
```

Document which actor or use case may perform each transition, which transitions
are idempotent, and which transitions are irreversible.

### Payment behavior

Represent payment methods behind a common processing contract. Each method may
have different limits, settlement timing, or failure behavior, while the
application service owns dispatch and idempotency.

### Events

Publish events only after the state change is durable. Include a stable event
identifier, event type, aggregate identifier, creation time, schema version,
and the minimum data required by consumers.

Consumers must tolerate duplicate delivery. A failed consumer should be
observable and replayable without charging a customer or sending duplicate
notifications.

### Long-running order workflow

Do not call an outbox plus idempotent consumers a Saga. Those patterns make
event publication and delivery reliable, but they do not coordinate a
multi-service business transaction or undo completed work.

For inventory, payment, and fulfillment, use an orchestrated Saga with durable
process state. The coordinator must issue idempotent step commands, persist
progress, apply bounded retries/timeouts, and compensate completed work when a
later step fails:

```text
Reserve inventory -> Charge payment -> Request fulfillment -> Confirm order
       |                    |                    |
       +-> reject order     +-> release stock    +-> refund/reverse payment
                                                    -> release stock
```

### Limited-stock flash sale

Include a high-contention inventory scenario, not only ordinary concurrent
requests. The inventory reservation must have one authoritative, atomically
enforced stock decision (for example, a conditional decrement or equivalent
transactional reservation), an idempotency key, expiry/release handling, and
an invariant that successful reservations never exceed available stock.

Prove the invariant with a coordinated high-parallelism test and a
representative load run. Rate limiting protects capacity and fairness; it must
not be the mechanism relied on for stock correctness.

## Milestone roadmap

Each milestone should produce working code, automated tests, documentation,
observable evidence, and a reproducible verification guide. Do not mark a
milestone complete based only on compilation.

### Milestone 0 — Project foundation

- Define the domain glossary and bounded responsibilities.
- Create the repository structure and local development workflow.
- Add configuration conventions, formatting, linting, and basic CI.
- Add a health endpoint and a minimal vertical slice.

**Proof:** a clean checkout can build, run, and execute its first automated
test.

### Milestone 1 — Synchronous order workflow

- Implement product reads and order creation.
- Validate requests and return consistent error documents.
- Persist orders and order lines with migrations and constraints.
- Add unit, integration, and API tests.

**Proof:** a request through the public API creates an order and it can be
retrieved from durable storage.

### Milestone 2 — Payments and state transitions

- Add payment processing behind method-specific strategies.
- Enforce one payment attempt or idempotency key according to the business
  rule.
- Implement explicit order state transitions.
- Model declined, pending, and completed outcomes.

**Proof:** success, decline, pending settlement, and duplicate-request flows
are all tested and documented.

### Milestone 3 — Events and reliable delivery

- Add an outbox or equivalent reliable publication mechanism.
- Publish order and payment events.
- Add at least one asynchronous consumer.
- Add consumer deduplication, retry, and failure visibility.

**Proof:** a committed business change eventually produces one effective
consumer side effect, including after duplicate delivery.

### Milestone 4 — Saga orchestration and inventory consistency

- Add durable Saga process state for inventory reservation, payment, and
  fulfillment.
- Make every command/event idempotent and define retry/timeout behavior.
- Compensate failed workflows by releasing inventory and refunding/reversing
  settled payments where appropriate.
- Add limited-stock reservation, expiry/release, and oversell prevention.

**Proof:** E2E tests cover successful completion, inventory rejection, payment
rejection, post-payment fulfillment failure, replay, recovery, and a
high-parallelism run where successful reservations never exceed stock.

### Milestone 5 — Security and boundary protection

- Start with authentication, token/session validation, and a role/permission
  matrix.
- Use OAuth2 resource-server validation at the gateway and protected service
  boundaries when defense in depth is required.
- Replace any custom demo issuer with a mature OAuth2/OIDC authorization
  server: authorization-code flow with PKCE, registered clients, OIDC
  discovery/UserInfo, refresh-token rotation, persistent signing keys, and
  safe key rotation.
- Define a role or permission matrix.
- Enforce authorization on every protected operation.
- Add negative tests for unauthenticated and unauthorized access.

**Proof:** authorization-code + PKCE, token expiry, wrong issuer/audience,
insufficient scope, refresh-token rotation/reuse rejection, signing-key
rotation, restart persistence, and the documented access matrix pass through
the gateway and protected resource-service boundaries.

### Milestone 6 — Resilience, rate limiting, and failure policy

- Add timeouts and bounded retries to remote calls.
- Add circuit breaking or an equivalent fail-fast mechanism.
- Define fail-open versus fail-closed behavior explicitly.
- Ensure fallback behavior never hides an invalid business result.
- Add distributed gateway rate limiting with Redis-backed token buckets shared
  across gateway replicas.
- Apply route-specific quotas: login by IP plus normalized username hash,
  anonymous reads by IP, authenticated writes by subject, and flash-sale
  reservations by subject plus sale/product.
- Return `429 Too Many Requests` and `Retry-After`; exclude health/metrics;
  define and test Redis-outage policy per route.

**Proof:** controlled dependency failure demonstrates retry, open-circuit,
fallback, recovery, rate-limit burst/refill, two-user fairness, shared
cross-replica quota, `429` behavior, and clear telemetry.

### Milestone 7 — Observability

- Add structured logs with correlation and trace identifiers.
- Expose application, dependency, and business metrics.
- Add distributed tracing across at least three components.
- Provide a dashboard for latency, errors, throughput, saturation, and queue
  health.

**Proof:** one business request can be followed from ingress to persistence,
messaging, and a consumer.

### Milestone 8 — Concurrency and performance case study

- Select one query or workflow with a measurable bottleneck.
- Capture a reproducible baseline workload.
- Inspect the query plan and relevant runtime metrics.
- Apply one focused change.
- Re-run the same workload and document both positive and negative results.
- Configure bounded consumer concurrency while preserving per-key/partition
  ordering where required.
- Add deterministic coordinated parallel-request tests for idempotency and
  limited-stock correctness.

**Proof:** before/after artifacts include workload parameters, measurements,
query-plan evidence, concurrency correctness, and an honest conclusion.

### Milestone 9 — Deployment, CI/CD, and operations

- Containerize services using secure runtime defaults.
- Add local orchestration and a production-like deployment target.
- Configure readiness, liveness, startup behavior, resource limits, and
  graceful shutdown.
- Build, test, and publish immutable images to a registry.
- Deploy a pinned image to a protected staging environment only through an
  approved workflow; run gateway smoke tests and document rollback.
- Document rollback, migration, backup, and infrastructure recovery steps.

**Proof:** a clean environment can deploy the platform and complete a full
business workflow through the public entry point, and the delivery workflow can
deploy and roll back a pinned version.

### Milestone 10 — Test-strength and engineering evidence

- Add architecture tests for dependency direction.
- Add contract tests for public and event schemas.
- Run mutation testing against selected domain/application packages.
- Close meaningful survived or uncovered mutants with behavior-focused tests.
- Record coverage and mutation results per service or bounded module.

**Proof:** quality reports are reproducible, scoped honestly, and tied to
business behavior rather than inflated aggregate percentages.

### Milestone 11 — Resource efficiency and capacity

- Record local hardware, Docker/Kubernetes allocation, JVM/container limits,
  data shape, warm-up, concurrency, duration, and background workload.
- Under the same conditions, measure CPU, process/container memory, heap/GC,
  connection pools, consumer lag, throughput, latency, failures, and
  CPU-throttling/HPA behavior where available.
- Make one focused efficiency change and compare it with the baseline.

**Proof:** reproducible laptop-sized artifacts show the conditions and
before/after result without claiming production-scale capacity from local data.

### Milestone 12 — Full regression and portfolio polish

- Add architecture decision records for important trade-offs.
- Re-run the complete automated suite and all affected Compose/Kubernetes E2E
  flows after cross-cutting changes to messaging, auth, concurrency, rate
  limiting, deployment, or resource settings.
- Document known limitations and deliberate non-goals.
- Add a concise architecture diagram and request-flow example.
- Provide a short demo recording or reproducible walkthrough.
- Explain how the design translates to another language or runtime.

**Proof:** a reviewer can understand the system, run the critical path, inspect
the evidence, identify the reasoning behind major decisions, and reproduce the
final regression results.

## Language translation guide

The following concepts should survive a language change even when the syntax
and libraries differ:

| Design intent | Possible implementations |
|---|---|
| Use-case boundary | application service, command handler, service object |
| Port | interface, protocol, trait, abstract contract |
| Adapter | handler, repository implementation, client, consumer |
| Domain invariant | entity method, aggregate rule, validator |
| Transaction | database transaction context, unit of work |
| Outbox | transactional table plus relay, CDC, durable event log |
| Consumer deduplication | inbox table, idempotency store, unique key, SETNX |
| Saga | durable process manager/orchestrator, state machine, compensating commands |
| Stock reservation | conditional update, optimistic/pessimistic lock, transactional reservation |
| Rate limiting | Redis token bucket/leaky bucket, gateway middleware/filter |
| Circuit breaker | library policy, middleware, stateful client wrapper |
| OAuth2/OIDC authorization server | mature ecosystem authorization-server package or managed identity provider |
| Structured error | problem document, typed error, error envelope |
| Dependency injection | framework container, composition root, explicit wiring |
| Contract test | consumer-driven contract, schema test, API compatibility test |
| Mutation testing | ecosystem-specific mutation runner |

When translating the project, preserve:

- dependency direction;
- transaction and consistency semantics;
- idempotency guarantees;
- authorization rules;
- event schema and delivery assumptions;
- Saga state, compensations, and recovery rules;
- concurrency and stock-correctness invariants;
- rate-limit identity keys and outage policies;
- OAuth2/OIDC protocol and token-validation requirements;
- timeout and retry budgets;
- test intent and acceptance criteria;
- operational signals and recovery procedures.

Do not translate framework annotations or library names mechanically. Recreate
the behavior and boundaries using the idioms of the target ecosystem.

## Definition of done

A feature or milestone is complete only when:

- the business behavior is implemented;
- invalid and failure paths are explicit;
- persistence and migration behavior are verified;
- asynchronous effects are reliable and idempotent;
- security rules are tested;
- metrics, logs, or traces make the behavior diagnosable;
- tests cover the contract at the appropriate level;
- the E2E path works through the real entry point;
- documentation explains how to reproduce and why the design exists;
- the change is independently reviewable and revertible.

## Deliberate non-goals

Avoid adding complexity merely to make the project look distributed.

- Do not split every class into a service.
- Do not introduce a broker when a transactionally consistent local operation
  is sufficient.
- Do not claim high availability without defining failure domains and
  recovery behavior.
- Do not claim performance improvement without comparable measurements.
- Do not claim production-scale capacity from a laptop benchmark.
- Do not use rate limiting as a substitute for transactionally safe inventory
  reservation.
- Do not implement OAuth2/OIDC protocol flows or cryptography from scratch
  when a mature authorization-server implementation fits the use case.
- Do not treat line coverage or mutation score as proof of correct requirements.
- Do not hide infrastructure limitations behind broad exception handling.

The strongest portfolio signal is not the number of technologies used. It is
the ability to make a clear engineering decision, implement it safely, measure
its behavior, and explain its trade-offs.
