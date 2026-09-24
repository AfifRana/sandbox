# AGENT HANDOFF — Order Management Platform

> This file is temporary session context. Do not commit it, reference it from
> project documentation, or treat it as part of the application.

## Project

`AfifRana/sandbox` is a Java 21/Spring Boot 3.3.4 order-management platform
covering hexagonal architecture, Oracle persistence, Kafka outbox/consumers,
Redis, JWT/OAuth2, resilience, observability, Docker, and Kubernetes.

Core working rules:

1. E2E-verify every milestone through the real gateway and infrastructure.
2. Every milestone needs code, an E2E guide, README/progress updates, a
   Conventional Commit, and an `orders-vX.Y.Z` tag.
3. Tags must point to the docs-inclusive milestone tip.
4. Document installable tools and Windows/Kubernetes recovery steps.
5. Do not commit this handoff file.
6. `main` is never touched; the user pushes manually.

## Non-negotiable guardrails for this repo

Full generic policy lives in `GENERAL-AGENT-GUIDE.md`. These are the
repo-specific traps that have already surfaced in this project; check them
before claiming any of the affected milestones done.

- Do not claim a Saga is done from outbox/inbox alone. It requires a durable
  coordinator, explicit states, compensation, replay, and recovery, each
  E2E-proven.
- Do not claim OAuth2/OIDC is done from the current JWT/JWKS issuer. It
  requires Authorization Code + PKCE, OIDC discovery/UserInfo, refresh-token
  rotation, and signing-key rotation.
- Do not claim flash-sale correctness from rate limiting alone. Inventory
  reservation must be atomic and proven not to oversell under real
  contention.
- Do not run PIT broadly across infrastructure-heavy packages; keep it
  scoped to application/domain logic per service, as already configured in
  the order-service, payment-service, and product-service `pom.xml` files.
- Do not report a Windows/minikube result without restarting the
  `kubectl port-forward` for `svc/demo-gateway` first; the tunnel does not
  survive a shell restart.
- Do not let `.._*` Maven artifact folders get tracked; `.gitignore` already
  excludes them, but verify none are staged before commit.
- Do not commit or reference `AGENT-HANDOFF.md`, `BACKEND-PROJECT-IDEATION.md`,
  or `GENERAL-AGENT-GUIDE.md` from tracked project docs; they stay untracked
  in this worktree and are propagated by the user manually.
- Do not overwrite an existing tag or push to `main`; only the user pushes to
  `main`, and moving a tag needs explicit approval.

## Repository and current branch state

- Repository: `git@github.com:AfifRana/sandbox.git`
- Intended user branch: `feature/java-playground`
- Current session worktree branch: `agents/agent-handoff-md-reading`
- Main checkout: `D:\Afif\Project\Exploration\sandbox`
- Current completed commits on this worktree:
  - `0579c25` — ignore mangled Maven `.._*` folders
  - `7b4135c` — add customer order-list endpoint
  - `95240ea` — add v0.9.0 performance implementation and artifacts
  - `c313ecc` — mark v0.9.0 complete in `docs/progress.md`
  - `2340c18` — add opt-in PIT mutation testing and v0.10.0 documentation
  - `decf64d` — cover all order-service PIT mutations
  - `1e99d14` — add payment-service PIT mutation coverage
  - `87a1bc5` — add product-service PIT mutation coverage
  - `3a8d46f` — fix the v0.10.0 E2E table header
  - `781090b` — track CD and concurrency work
  - `1fa0a31` — define required backend capability coverage
  - `9dfeb78` — add the Saga roadmap
  - `0d16a61` — plan flash-sale and security coverage
  - `d0f9685` — add the OAuth2/OIDC roadmap
  - `7dbde9a` — add the resource-efficiency/capacity study
  - `644f0b1` — add the distributed rate-limiting roadmap
- Local tags: `orders-v0.9.0` points to `c313ecc`; `orders-v0.10.0` points to
`3a8d46f`; `orders-v0.10.1` was deleted.
- `AGENT-HANDOFF.md` is intentionally untracked.
- `GENERAL-AGENT-GUIDE.md` and `BACKEND-PROJECT-IDEATION.md` are also
  intentionally untracked and were updated in this session:
  - `GENERAL-AGENT-GUIDE.md` gained a "Guardrail checklist (read first)"
    section and a "SOLID and clean-code discipline" subsection under Core
    operating principles.
  - `BACKEND-PROJECT-IDEATION.md` gained a "Code quality and design
    principles" subsection (SOLID, Clean Code, DRY, KISS/YAGNI, Law of
    Demeter) under "Expertise the project should showcase," framed for
    interview talking points.
  - All three files are still awaiting manual copy to the dedicated docs
    branch; do not assume that has happened yet.
- To propagate the completed commits to `feature/java-playground`, from the main
  checkout run:

  ```powershell
  Set-Location 'D:\Afif\Project\Exploration\sandbox'
  git switch feature/java-playground
  git cherry-pick 0579c25 7b4135c 95240ea c313ecc 2340c18 decf64d 1e99d14 87a1bc5 3a8d46f 781090b 1fa0a31 9dfeb78 0d16a61 d0f9685 7dbde9a 644f0b1
  git tag orders-v0.9.0
  git tag -f orders-v0.10.0 3a8d46f
  git push origin feature/java-playground --follow-tags
  ```

  Use `git merge-base --is-ancestor feature/java-playground
  agents/agent-handoff-md-reading` first if checking whether a fast-forward is
  possible. Do not overwrite an existing remote tag without user approval.

All commits use Conventional Commits and include:

```text
Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

## Milestone history

| Tag | Milestone |
|---|---|
| `orders-v0.1.0` | Six-service scaffold: REST → Oracle → outbox → Kafka → consumers |
| `orders-v0.2.0` | Product Oracle persistence and Redis cache-aside |
| `orders-v0.3.0` | Payment Strategy pattern, idempotency, CREATED → PAID |
| `orders-v0.4.0` | JWT/OAuth2, gateway, resource servers, role matrix |
| `orders-v0.5.0` | Transactional inbox and Redis SETNX consumer deduplication |
| `orders-v0.6.0` | Resilience4j retry/circuit breaker and authoritative pricing |
| `orders-v0.7.0` | Prometheus, Grafana, Micrometer Tracing, OTel, Jaeger |
| `orders-v0.8.0` | Minikube + Helm deployment, probes, HPA, full lifecycle |
| `orders-v0.9.0` | Customer order-list endpoint, k6 workload, Oracle plan study |
| `orders-v0.10.0` | Opt-in PIT mutation testing for order-service, payment-service, and product-service application/domain logic, 100% score |

## v0.9.0 completed work

Code:

- `GET /api/v1/orders?customerId=<uuid>&limit=<positive-int>` returns recent
  orders in `createdAt DESC` order.
- The endpoint is wired through the hexagonal port, JPA adapter, use case, and
  controller.
- `V3__add_customer_created_at_index.sql` drops the redundant
  `idx_orders_customer_id` and creates
  `idx_orders_customer_created_at (customer_id, created_at DESC)`.
- `.gitignore` contains the exact `.._*` pattern to prevent mangled Maven
  folders from being tracked.

Performance artifacts:

- `load-tests/k6/baseline.js`
- `docs/perf/seed_orders.sql`
- `docs/perf/explain-before.txt`
- `docs/perf/explain-after.txt`
- `docs/perf/k6-before.txt`
- `docs/perf/k6-after.txt`
- `docs/e2e/e2e-v0.9.0.md`

The k6 script:

- Logs in once during `setup()` using `K6_USERNAME`/`K6_PASSWORD`, defaulting
  to `alice/password`.
- Defaults to the focused GET query workload; set `K6_CREATE_ORDER=true` to
  include order creation.
- Supports `GATEWAY_URL`, `CUSTOMER_ID`, `K6_VUS`, and `K6_DURATION`.
- Reports p95 and p99 and requires less than 1% failed requests.

Captured final run, two VUs for 30 seconds:

| State | Plan access path | Requests | p95 | p99 | Failed |
|---|---|---:|---:|---:|---:|
| Before V3 | `IDX_ORDERS_CUSTOMER_ID` + `SORT ORDER BY STOPKEY` | 55 | 199.05 ms | 257.80 ms | 0% |
| After V3 | `IDX_ORDERS_CUSTOMER_CREATED_AT` + `SORT ORDER BY STOPKEY` | 49 | 369.26 ms | 3.90 s | 0% |

Important conclusion: Oracle selected the composite index after V3, but kept
the sort and the repeated k6 run did not show a speedup. The guide documents
this negative result honestly; do not rewrite it as a guaranteed optimization.

Validation completed:

- `mvn -pl services/order-service test`: 16/16 passed.
- `k6 inspect load-tests/k6/baseline.js`: passed.
- Login and list-orders smoke test through the Kubernetes gateway: passed.
- Both final k6 runs: 0% HTTP failures.
- Oracle before/after plans captured.
- No `.._*` folders remain after cleanup.

## v0.10.0 completed work

- Added opt-in `pitest-maven` 1.17.4 and `pitest-junit5-plugin` 1.2.1 to
  `services/order-service/pom.xml`.
- PIT targets `com.example.order.application.*` and
  `com.example.order.domain.*`; normal Maven test/verify remains unchanged.
- Verified command:
  `mvn -pl services/order-service org.pitest:pitest-maven:mutationCoverage`
- Follow-up `GetOrderUseCaseTest` covers both previously unexecuted delegation
  paths.
- Current result: 13 mutations generated, 13 killed, 100% mutation score, 98%
  mutated line coverage, 100% test strength, 0 survived mutations, and 0
  no-coverage mutations.
- Added the same opt-in PIT configuration to `payment-service`, targeting
  `com.example.payment.application.*` and `com.example.payment.domain.*`.
- Added `PaymentProcessorStrategyTest` for strategy registration, boundary
  behavior, declines, and pending bank transfers.
- Payment-service result: 17 mutations generated, 17 killed, 100% mutation
  score, 0 survived mutations, and 0 no-coverage mutations.
- Added the same opt-in PIT configuration to `product-service`, targeting
  `com.example.product.application.*` and `com.example.product.domain.*`.
- Product-service result: 6 mutations generated, 6 killed, 100% mutation
  score, 0 survived mutations, and 0 no-coverage mutations.
- HTML reports are local under
  `services/<service>/target/pit-reports/index.html` and are not committed.
- Normal order-service, payment-service, and product-service test suites remain
  green.
- Guide: `docs/e2e/e2e-v0.10.0.md`.

## Demo facts

- Login: `POST /auth/login` with `{"username":"alice","password":"password"}`.
- Users: `alice=CUSTOMER`, `bob=ADMIN`, `carol=CUSTOMER+ADMIN`.
- Customer ID:
  `11111111-1111-1111-1111-111111111111`.
- Mechanical Keyboard product:
  `22222222-2222-2222-2222-222222222222`, authoritative price `89.99`.
- Unknown product tests should use `99999999-...`.
- Order POST uses a `lines` array, not flat product fields.
- Payment is synchronous REST, then publishes `payment.paid`; the order changes
  from CREATED to PAID asynchronously.

## Runtime environment

Windows host, Docker Desktop, minikube with an 8 GB WSL node:

- Minikube binary:
  `C:\Program Files\Kubernetes\Minikube\minikube.exe`
- Kubernetes namespace: `order-platform`
- Helm release metadata is in the default namespace; workloads are in
  `order-platform`.
- Windows cannot reliably reach minikube NodePort IP `192.168.49.2`; use:

  ```powershell
  kubectl port-forward -n order-platform svc/demo-gateway 30000:8080
  ```

- Pods reach host infrastructure through `host.minikube.internal`, not
  `host.docker.internal`.
- The v0.9.0 port-forward has exited; restart it before Kubernetes E2E work.

Start/recover the cluster:

```powershell
& 'C:\Program Files\Kubernetes\Minikube\minikube.exe' start --driver=docker
& 'C:\Program Files\Kubernetes\Minikube\minikube.exe' update-context
kubectl get nodes
```

Start only host infrastructure, with both variables in the same shell:

```powershell
$env:PROMETHEUS_CONFIG='prometheus-k8s.yml'
$env:KAFKA_EXTERNAL_ADVERTISED_HOST='host.minikube.internal'
docker compose -f 'D:\Afif\Project\Exploration\sandbox\docker-compose.yml' up -d `
  oracle kafka redis prometheus grafana otel-collector jaeger
```

Do not start the six Compose application services when using Kubernetes.
They waste memory and can conflict with ports.

Known small-node boot-storm recovery:

```powershell
kubectl scale deployment/demo-order -n order-platform --replicas=0
# wait for node load to settle
kubectl scale deployment/demo-order -n order-platform --replicas=2
```

### Shutdown after E2E or milestone completion (required)

When an E2E run is finished and its evidence is captured, or after a
milestone is committed/tagged, tear the runtime down. Do not leave the
11-JVM Kubernetes node plus Oracle/Kafka containers running between
sessions — it starved the host during the v0.11.0 work and caused
repeated apiserver timeouts and node NotReady events.

```powershell
# 1. Stop all Kubernetes workloads (prevents crash-loop boot storm on
#    next start; keeps images and release data)
kubectl scale deployment --all -n order-platform --replicas=0

# 2. Stop the cluster (keeps loaded images, Oracle volume, Helm release;
#    much faster to resume than minikube delete)
minikube stop

# 3. Stop host infrastructure
docker compose -f 'D:\Afif\Project\Exploration\sandbox\docker-compose.yml' stop `
  oracle kafka redis prometheus grafana otel-collector jaeger

# 4. Kill any kubectl port-forward processes still bound to host ports
#    (30000/30084/30087 etc.) — they die with the cluster but can linger
Get-NetTCPConnection -LocalPort 30000,30084,30087 -State Listen -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

`minikube delete` is only for an explicit clean-slate request from the
user: it discards loaded images, the Oracle volume, and the Helm release,
forcing a full `docker compose build` + `minikube image load` cycle.

Restart after teardown = the two blocks above in reverse: `minikube start`
+ `minikube update-context`, then the infra `docker compose up -d` with
both env vars set in the same shell, then re-establish port-forwards.


## Useful commands

Targeted tests:

```powershell
mvn -pl services/order-service test
```

Inspect for Maven junk:

```powershell
Get-ChildItem services -Directory -Recurse |
  Where-Object { $_.Name -like '.._*' }
```

Run the focused k6 workload:

```powershell
$env:GATEWAY_URL='http://127.0.0.1:30000'
$env:K6_VUS='2'
$env:K6_DURATION='30s'
$env:K6_USERNAME='alice'
$env:K6_PASSWORD='password'
$env:K6_CREATE_ORDER='false'
k6 run load-tests/k6/baseline.js
```

## Next backlog

Priority after v0.9.0, as recorded in `docs/progress.md`:

1. Saga orchestration: add durable order-process state to coordinate inventory
   reservation, payment, and fulfillment; support idempotent commands/events,
   timeout/retry policy, and compensations that release inventory and
   refund/reverse payment. E2E-verify success, inventory/payment rejection,
   post-payment fulfillment failure, replay, and recovery.
2. Test-first edge-case policy: for every upcoming correctness-critical
   milestone, state the invariant and write the failing focused behavior test
   before implementation; add an Edge cases verified table to the E2E guide.
   Do not claim universal strict TDD for completed historical work.
3. CI/CD delivery: publish immutable service images to GHCR and add a
   protected, approved staging Helm deployment using pinned images, gateway
   smoke tests, and documented rollback.
4. Multithreading/concurrency: configure bounded Kafka listener concurrency
   while preserving per-key ordering; prove idempotency under coordinated
   parallel requests; capture virtual-thread, listener, and HikariCP metrics.
5. Flash-sale inventory reservation: implement authoritative limited-stock
   reservation with atomic conditional decrement or equivalent transactionally
   safe reservation, idempotency keys, expiry/release, contention controls,
   and oversell prevention. Run a high-parallelism E2E/load test that proves
   successful reservations never exceed stock.
6. Distributed gateway rate limiting: use Redis-backed token buckets shared by
   all gateway replicas. Return `429` and `Retry-After`; limit login by IP plus
   normalized username hash, anonymous reads by IP, authenticated writes by JWT
   subject, and flash-sale reservations by subject plus sale/product. Exclude
   health/metrics. E2E-verify burst/refill, fairness, shared cross-replica
   quotas, explicit Redis outage policy, and limiter metrics.
7. Full regression revalidation after the Saga, CI/CD, concurrency, flash-sale,
   and rate-limiting milestones:
   Maven verification, focused PIT, Docker Compose and Kubernetes E2E flows,
   observability checks, and k6 workload.
8. OAuth2/OIDC authorization server: replace the custom issuer with a mature
   standards-based implementation. Implement authorization-code flow with
   PKCE, registered clients, OIDC discovery/UserInfo, persistent signing keys
   with safe rotation, refresh-token rotation, and issuer/audience/scope
   validation. E2E-verify authorization, expiry, insufficient scope, refresh
   reuse rejection, key rotation, restart persistence, Compose, and Kubernetes.
9. Final regression revalidation after OAuth2/OIDC: repeat Maven verification,
   focused PIT, Docker Compose and Kubernetes E2E flows, observability checks,
   and k6 workload; update all affected E2E guides with actual results.
10. Resource-efficiency/capacity case study: run a reproducible laptop-sized
   workload and record host hardware, Docker Desktop/minikube allocation,
   JVM/container limits, data, warm-up, concurrency, duration, and background
   load. Compare baseline and post-change CPU, memory, GC, HikariCP, Kafka lag,
   throughput, latency, failures, and Kubernetes throttling/HPA data where
   available. Do not claim production-scale capacity from local results.
11. Final regression revalidation after the resource-efficiency milestone:
   repeat Maven verification, focused PIT, Docker Compose and Kubernetes E2E
   flows, observability checks, k6, and the resource workload.
12. ADRs after all code changes and full regression: Kafka versus RabbitMQ,
   transactional outbox, Saga orchestration, and the chosen flash-sale
   concurrency/consistency strategy, distributed rate limiting, plus OAuth2/OIDC
   authorization-server and resource-efficiency trade-offs.
13. Demo GIF/video for the README.
14. Refresh known limitations and next steps before publishing.

The project is required to fully demonstrate synchronous and asynchronous
programming, design patterns, containerization, orchestration, CI/CD,
multithreading/concurrency, extreme-concurrency flash sales, caching,
distributed rate limiting, Saga orchestration, OAuth2/OIDC authorization, and
resource efficiency. `README.md` and `docs/notes.md` contain the authoritative
implementation/proof criteria. Do not frame unfinished required capabilities
as optional gaps or permanent limitations.
The current transactional outbox/inbox flow is not a Saga: it has no durable
process coordinator or compensating actions. The required Saga milestone is an
orchestrated workflow documented in the tracked README, notes, and progress
files.

Before declaring another milestone complete, update the relevant E2E guide,
`docs/e2e/README.md`, `docs/progress.md`, `README.md`, commit with the required
trailer, and tag the docs-inclusive tip.
