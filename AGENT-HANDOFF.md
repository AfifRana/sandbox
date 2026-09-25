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
  the order-service, payment-service, product-service, inventory-service,
  fulfillment-service, and saga-orchestrator-service `pom.xml` files.
- Do not report a Windows/minikube result without restarting the
  `kubectl port-forward` for `svc/demo-gateway` first; the tunnel does not
  survive a shell restart.
- Do not let `.._*` Maven artifact folders get tracked; `.gitignore` already
  excludes them, but verify none are staged before commit.
- Do not commit or reference `AGENT-HANDOFF.md` from tracked project docs;
  it stays session-local in this worktree. `GENERAL-AGENT-GUIDE.md` and
  `BACKEND-PROJECT-IDEATION.md` live on the dedicated `docs` branch
  (`origin/docs`), which the user maintains manually.
- Do not overwrite an existing tag or push to `main`; only the user pushes to
  `main`, and moving a tag needs explicit approval.
- Do not start the six Compose application services when using Kubernetes;
  they waste memory and conflict with ports.
- Do not leave the runtime running after E2E/milestone work — see the
  required shutdown block in Runtime environment.

## Repository and current branch state

- Repository: `git@github.com:AfifRana/sandbox.git`
- Intended user branch: `feature/java-playground`
- Current session worktree branch: `agents/saga-orchestration`
- Main checkout: `D:\Afif\Project\Exploration\sandbox` (on
  `feature/java-playground` at `50ed67e`)
- Saga worktree: `D:\Afif\Project\Exploration\sandbox-saga` (this file lives
  here; all v0.11.0 work happened in this worktree)
- Current completed commits on this worktree (since `feature/java-playground`
  tip `50ed67e`):
  - `294a55f` — docs: adopt test-first edge-case policy
  - `2a44e52` — checkpoint: inventory-service + fulfillment-service scaffold
    and the three agent guide files (session checkpoint commit; its guide-file
    and participant-service content is included in `9b47534`)
  - `9b47534` — feat: saga orchestration with fulfillment-service and
    saga-orchestrator-service (the v0.11.0 milestone commit)
  - `2c28ed2` — docs: require runtime teardown after E2E or milestone
    completion (current branch tip)
- Local tags: `orders-v0.9.0` → `c313ecc`; `orders-v0.10.0` → `3a8d46f`;
  `orders-v0.11.0` → `2c28ed2` (docs-inclusive tip).
- Old session branch `agents/agent-handoff-md-reading` still exists and points
  at `2a44e52`; it is fully contained in `agents/saga-orchestration` and can
  be deleted.
- `feature/java-playground` is a strict ancestor of
  `agents/saga-orchestration` (fast-forward possible, no cherry-pick needed).
- To propagate to the user branch, from the main checkout run:

  ```powershell
  Set-Location 'D:\Afif\Project\Exploration\sandbox'
  git switch feature/java-playground
  git merge --ff-only agents/saga-orchestration
  git push origin feature/java-playground --follow-tags
  ```

  This carries `294a55f`, `2a44e52`, `9b47534`, `2c28ed2` and the
  `orders-v0.11.0` tag. Do not overwrite an existing remote tag without user
  approval.
- `GENERAL-AGENT-GUIDE.md` and `BACKEND-PROJECT-IDEATION.md` are tracked on
  the dedicated `docs` branch (`origin/docs`), which the user maintains
  manually. As of 2026-09-25 `origin/docs` (`0e855ac`) is content-identical
  to this branch's copies — the teardown additions are already pushed there.
  `AGENT-HANDOFF.md` is intentionally NOT on the docs branch; it stays
  session-local in this worktree.

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
| `orders-v0.11.0` | Saga orchestration: durable coordinator, inventory reservation → fulfillment, compensation, replay, restart recovery |

## v0.11.0 completed work (Saga orchestration)

Code:

- **saga-orchestrator-service** (`services/saga-orchestrator-service/`):
  - Durable saga state machine in `OrderSagaUseCase`:
    `STARTED → INVENTORY_RESERVED → COMPLETED` (happy),
    `STARTED → REJECTED` (reservation rejection),
    `INVENTORY_RESERVED → COMPENSATED` (fulfillment failure → inventory
    release).
  - Every transition persists the saga row and the outgoing command's outbox
    row in one transaction (transactional outbox, `saga_process` +
    `saga_outbox_events`, Flyway V1).
  - `SagaEventListener` consumes `inventory-events` + `fulfillment-events`
    and dispatches on the explicit `type` field of `{type, payload}`
    envelopes; topic-based inference remains only as a legacy fallback.
  - `SagaController` exposes `POST /api/v1/sagas` (ROLE_CUSTOMER/ADMIN).
  - Restart recovery: `recoverPending()` runs on startup
    (`ApplicationRunner`) and every 30s (`@Scheduled`); re-issues the pending
    command for non-terminal sagas. Participants deduplicate by sagaId.
- **fulfillment-service** (`services/fulfillment-service/`): ship participant
  with `fulfillment_ledger` idempotency, transactional outbox, and a
  deterministic failure sentinel — product `77777777-...`
  (`ShipOrderUseCase.ALWAYS_FAILS_PRODUCT_ID`) always fails shipment, for
  compensation testing.
- **inventory-service** (added in checkpoint `2a44e52`): reserve/release with
  `reservation_ledger` idempotency, stock table, command listener, outbox.
  Sentinel seed data: product `66666666-...` has 0 stock (rejection path),
  `77777777-...` has 500 stock (fulfillment-failure path); seeded via
  `services/product-service/.../V2__seed_saga_sentinel_products.sql` and the
  inventory V1 migration.
- **Envelope fix**: inventory/fulfillment outbox relays publish
  `{"type":"...","payload":{...}}` envelopes. Without the discriminator a
  stock rejection is indistinguishable from a reservation and the saga
  completes wrongly — this bug was found and fixed during E2E.
- Reactor: both new modules enabled in root `pom.xml`; Helm chart extended
  (inventory 30085, fulfillment 30086, saga 30087; DB_URL/KAFKA_BROKERS env
  wiring in `templates/service.yaml`).

E2E verified on minikube (guide: `docs/e2e/e2e-v0.11.0.md`):

| Scenario | Result |
|---|---|
| Happy path (product 2222, qty 3) | saga `COMPLETED`; reservation `RESERVED`; fulfillment `SHIPPED`; stock 1000 → 997/3 |
| Inventory rejection (product 6666, 0 stock) | saga `REJECTED` with reason; no shipment attempt |
| Fulfillment failure (product 7777 sentinel) | saga `COMPENSATED`; reservation `RELEASED`; stock restored 500/0 |
| Replay idempotency | no duplicate ledger rows per sagaId; stock decremented once |
| Restart recovery | 2 sagas stranded `STARTED` from a broker outage driven to terminal states by the startup recovery |

Final `saga_process` distribution: 3 `COMPLETED`, 3 `REJECTED`,
1 `COMPENSATED`, 0 `STARTED`.

Regression: full reactor `mvn verify` BUILD SUCCESS (71 tests, 10 modules).
PIT: saga-orchestrator 17/19 killed (89%, test strength 94%), inventory
16/17 (94%), fulfillment 11/12 (92%) — all above the 70 threshold.

Known deferrals (documented in the guide):

- Payment coordination and refund/reverse compensation are NOT implemented —
  payment-service has no reversal capability. The saga coordinates inventory
  and fulfillment only. Do not describe the saga as coordinating payment.
- Participant outboxes previously published bare payloads; the saga listener
  still accepts bare payloads via the legacy topic fallback, but new
  participants must publish envelopes.

New runtime gotchas surfaced during v0.11.0 (also in the helm README):

- Compose must run with `KAFKA_EXTERNAL_ADVERTISED_HOST=host.minikube.internal`
  or sagas hang in `STARTED` forever (pods bootstrap but produce/consume
  dead-ends on pod-local localhost).
- 11 JVMs on the 8GB node cause boot storms, apiserver TLS timeouts, and
  node NotReady; scale `demo-order` to 1 and idle services to 0 while
  running saga E2E. Always tear down after finishing (see Runtime
  environment).
- After a host reboot the minikube apiserver port changes: run
  `minikube update-context` (and `minikube start` if the container is
  Stopped).

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
- v0.11.0 extended the same opt-in PIT pattern to inventory-service,
  fulfillment-service, and saga-orchestrator-service (results in the v0.11.0
  section above).

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

Saga E2E facts (v0.11.0):

- Start a saga: `POST /api/v1/sagas` with
  `{"orderId":"<uuid>","lines":{"<productId>":qty}}` + Bearer token; returns
  201 with `sagaId`. Saga service NodePort 30087 (port-forward it).
- Saga state lives in Oracle table `saga_process` (schema `ORDERS`); query
  with `sqlplus -s orders/orders@localhost/FREEPDB1` inside the
  `order-processing-platform-oracle-1` container. `RAWTOHEX(saga_id)` gives
  the UUID hex.
- Sentinel products: `66666666-...` = 0 stock (rejection path);
  `77777777-...` = fulfillment always fails (compensation path);
  `22222222-...` = 1000 stock (happy path).
- Participant ledgers: `reservation_ledger` and `fulfillment_ledger`, both
  keyed by sagaId with exactly one row per saga.
- Kafka topics: commands `inventory-commands`/`fulfillment-commands`; replies
  `inventory-events`/`fulfillment-events` (envelope format
  `{"type":"...","payload":{...}}`).

## Runtime environment

Windows host, Docker Desktop, minikube with an 8 GB WSL node:

- Minikube binary:
  `C:\Program Files\Kubernetes\Minikube\minikube.exe`
- Kubernetes namespace: `order-platform`
- Helm release metadata is in the default namespace; workloads are in
  `order-platform`. The release is named `demo` (not `order-platform`) —
  upgrade with `helm upgrade demo deploy/helm/order-platform` (run from the
  repo root; do NOT use `--reuse-values` after editing `values.yaml`).
- Windows cannot reliably reach minikube NodePort IP `192.168.49.2`; use:

  ```powershell
  kubectl port-forward -n order-platform svc/demo-gateway 30000:8080
  ```

- Pods reach host infrastructure through `host.minikube.internal`, not
  `host.docker.internal`.
- Port-forwards do not survive shell/session restarts; re-establish them
  before any Kubernetes E2E work.
- **Current state (verified 2026-09-26): the whole stack is down, including
  Docker Desktop.** All deployments were scaled to 0, `minikube stop` was
  executed, and all 7 infra containers were stopped. Docker Desktop itself is
  not running, so `docker` and `minikube status` fail with a daemon/pipe error
  (`npipe:////./pipe/dockerDesktopLinuxEngine`) — that is expected, not a
  broken install. Start Docker Desktop first, then follow the blocks below.
  No port-forwards are running.

Start/recover the cluster:

```powershell
# Docker Desktop must be running first (the docker driver reuses its daemon)
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
kubectl scale deployment/demo-order -n order-platform --replicas=1
```

Also useful during E2E: scale idle services to 0 to free CPU for the
services under test (`demo-product`, `demo-notification` are safe to scale
to 0 for saga E2E; keep `demo-auth` up for logins).

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
The v0.11.0 images (inventory, fulfillment, saga) are already loaded into
minikube's daemon and the Helm release `demo` is at revision 4 with all
three new services — after restart, scale deployments back up as needed
(`kubectl scale deployment --all -n order-platform --replicas=1`).

## Useful commands

Targeted tests:

```powershell
mvn -pl services/order-service test
```

Full reactor verification:

```powershell
mvn verify
```

Focused PIT (opt-in, per service):

```powershell
mvn -pl services/saga-orchestrator-service org.pitest:pitest-maven:mutationCoverage
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

Query saga state in Oracle (container must be running):

```powershell
"SELECT RAWTOHEX(saga_id), status FROM saga_process ORDER BY created_at DESC FETCH FIRST 5 ROWS ONLY;`nEXIT;" |
  docker exec -i order-processing-platform-oracle-1 sh -c "cat > /tmp/q.sql"
docker exec order-processing-platform-oracle-1 sqlplus -s "orders/orders@localhost/FREEPDB1" "@/tmp/q.sql"
```

## Next backlog

Priority after v0.11.0, as recorded in `docs/progress.md`:

1. ~~Saga orchestration~~ — DONE in v0.11.0 for inventory → fulfillment with
   release compensation. Remaining saga scope (payment coordination,
   refund/reverse compensation) is deferred until payment-service gains a
   reversal capability; do not re-implement inventory/fulfillment coordination.
2. ~~Test-first edge-case policy~~ — DONE: adopted for the Saga milestone
   (OrderSagaUseCaseTest written first, 9 tests). Continue applying it to
   every upcoming correctness-critical milestone; add an Edge cases verified
   table to each new E2E guide.
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

The v0.11.0 saga coordinates inventory and fulfillment only. The required
Saga capability still needs payment coordination with refund/reverse
compensation once payment-service gains a reversal capability — do not
describe the saga as fully coordinating payment today.

Before declaring another milestone complete, update the relevant E2E guide,
`docs/e2e/README.md`, `docs/progress.md`, `README.md`, commit with the required
trailer, and tag the docs-inclusive tip. Then tear down the runtime (see
Runtime environment).

## Session-switch checklist (for the next agent)

1. Read this file fully; verify branch/tag claims against
   `git log`/`git tag` in the saga worktree
   (`D:\Afif\Project\Exploration\sandbox-saga`).
2. Runtime is fully down (see Runtime environment) — Docker Desktop included.
   Start Docker Desktop, then `minikube start` + `minikube update-context`,
   then the compose `up -d` block with both env vars in the same shell, then
   re-establish port-forwards. Start it only when needed.
3. **First outstanding task: update `README.md` for v0.11.0** (see open item 1
   in Session state). Confirm with the user before moving the
   `orders-v0.11.0` tag.
4. After that, the next milestone is CI/CD delivery (backlog item 3). Its
   authoritative criteria are in `docs/progress.md` and `docs/notes.md`.
5. Work in the saga worktree on `agents/saga-orchestration` (or a new
   `agents/<milestone>` branch off it); never touch `main`.
6. After finishing: update docs, commit with the trailer, tag
   `orders-vX.Y.Z` on the docs-inclusive tip, tear down the runtime, and
   update this handoff file.


## Session state (2026-09-26, saga worktree) — reconciled against the worktree

This section supersedes any older mid-session snapshot. Verified by inspecting
the worktree, `git log`, `git tag`, and the docs on 2026-09-26.

Completed and committed:

- Branch `agents/saga-orchestration` at `2c28ed2`; tag `orders-v0.11.0`
  points at the same commit.
- `9b47534` is the v0.11.0 milestone commit: fulfillment-service,
  saga-orchestrator-service, the envelope fix in inventory-service's outbox
  relay, root `pom.xml` modules, Helm chart (inventory 30085 / fulfillment
  30086 / saga 30087), `docs/e2e/e2e-v0.11.0.md`, `docs/e2e/README.md`,
  and `docs/progress.md`.
- `2c28ed2` added the mandatory teardown policy to `AGENT-HANDOFF.md` and
  `GENERAL-AGENT-GUIDE.md`.
- Saga E2E is fully verified (see the v0.11.0 section). The earlier "e2e
  in progress" state is obsolete.
- Runtime is torn down, including Docker Desktop (see Runtime environment).

Open items for the next session:

1. **`README.md` was NOT updated for v0.11.0** — this is the one remaining
   milestone-documentation gap. It still says "Saga orchestration (planned)",
   lists only six services in the Services table, and leaves the roadmap
   boxes for "Test-first edge-case policy" and "Saga orchestration" unchecked.
   The project convention (and this file's checklist) requires the README
   update to accompany the milestone. Fixing it means the `orders-v0.11.0`
   tag no longer points at the docs-inclusive tip, so **moving the tag needs
   explicit user approval**.
2. `AGENT-HANDOFF.md` is currently **tracked** on this branch (committed in
   `2c28ed2`), even though the file itself says it is session-local and must
   not be committed. Resolve this contradiction with the user: either keep it
   tracked deliberately, or `git rm --cached AGENT-HANDOFF.md` and gitignore
   it. Do not silently change the tracking policy.
3. Open design questions carried over: should order-service drive saga start
   as part of its order flow (today the saga is started by a direct
   `POST /api/v1/sagas`)? Payment refund/reversal compensation remains out of
   scope until payment-service gains that capability.

Verified facts worth reusing:

- 9 Maven modules and 9 services; `docker-compose.yml` still defines only the
  original 6 application services (inventory, fulfillment, and saga run
  Kubernetes-only).
- PIT is opted in for 6 services: order, payment, product, inventory,
  fulfillment, saga-orchestrator — all scoped to `application`/`domain`.
- Sentinel fixtures: product `22222222-…` 1000 stock (happy path),
  `66666666-…` 0 stock (rejection), `77777777-…` 500 stock but fulfillment
  always fails (compensation). Seeds: inventory V1 and product
  `V2__seed_saga_sentinel_products.sql`.
- `GENERAL-AGENT-GUIDE.md` and `BACKEND-PROJECT-IDEATION.md` on this branch
  are blob-identical to `origin/docs` (`0e855ac`).
- `feature/java-playground` is a strict ancestor (4 commits behind), so
  `git merge --ff-only` works.
- PowerShell console encoding is CP1252 on this host: writing UTF-8 text
  through a shell redirect or console pipe mojibakes non-ASCII characters
  (this file was corrupted that way and had to be recovered). Use the editor
  tools or an explicit `-Encoding utf8NoBOM` / Python `encoding="utf-8"`
  when writing files containing `→` or `—`.
- PowerShell quirks: no `&&`; `docker`/`mvn` may report exit 1 on success —
  check the output content, not the exit code.

