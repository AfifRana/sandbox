# AGENT HANDOFF — Order Management Platform (Job Portfolio Project)

> **Purpose:** One-time brief for the AI agent continuing this work on a new
> device. Read this fully before doing anything. The user will DELETE this
> file once handoff is complete — do not commit it, do not reference it in
> docs, do not build on it.

---

## 1. Why this project exists

The user is a senior Java developer candidate (5+ yrs) targeting a job with
these requirements: Java/Spring Boot, REST APIs, Oracle + SQL optimization,
microservices, JUnit/Mockito, design patterns & clean code, Docker/CI/CD,
Kafka/RabbitMQ/Redis Streams (bonus), Kubernetes/Cloud (bonus).

This repo (`AfifRana/sandbox`, branch `feature/java-playground`) contains a
purpose-built **Order Management Platform** microservices portfolio — each
milestone maps to a job requirement and is E2E-verified so the user can
reproduce everything live in an interview.

**User's core rules (learned over 7 milestones — follow them):**
1. Every milestone must be **E2E verified for real** (curl through the
   gateway, actual Docker runs) before being called done. No "should work".
2. Every milestone gets: an **E2E reproduction guide** in `docs/e2e/`
   (separate from README — user explicitly forbade E2E in README), a
   **progress.md** update, a **README** update, a **commit**, and a **tag**.
3. **Document every tool that must be installed** on a device (user moves
   between machines and hates undocumented prerequisites). See
   `deploy/helm/order-platform/README.md` prerequisites table for the format.
4. Tags are named `orders-vX.Y.Z` and are placed on the **docs-inclusive tip
   commit** (code + docs in one commit, tag points at it). progress.md's tag
   table must describe what each tag marks, because the user checks out tags
   and reads progress.md to see "what we've done in that tag".
5. Stale E2E guides get **deleted** when superseded — keep `docs/e2e/`
   current, not append-only.

---

## 2. Repository & workflow mechanics

- **Repo:** `git@github.com:AfifRana/sandbox.git` (SSH). The agent
  environment may NOT have push access — the user pushes manually.
- **Working branch:** `feature/java-playground` (user's branch, pushed manually).
- **Agent branch:** `agents/job-application-strategy-senior-java-dev`
  (worktree at `D:\Project\sandbox.worktrees\job-application-strategy-senior-java-dev`
  on the OLD device — on the new device this worktree arrangement may not
  exist; work wherever the session starts, but ALWAYS finish by:
  `git merge --ff-only <agent-branch> ` into `feature/java-playground` from
  the main checkout, then tell the user to push).
- **`main` is NEVER touched.**
- **Commit convention:** Conventional Commits
  (`feat(scope): ...`, `docs(e2e): ...`, `chore(docker): ...`), body explains
  why, and ALWAYS this trailer:
  ```
  Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
  ```
- **Tag flow:** commit code+docs → ff-merge to feature branch →
  `git tag orders-vX.Y.Z` → remind user to
  `git push origin feature/java-playground --follow-tags`.
- **Current tip:** `be22b97` "feat(k8s): add draft Helm chart ... (unverified)".
  Tags v0.1.0–v0.7.0 exist locally; **verify what's pushed with
  `git ls-remote --tags origin`** — as of handoff, push status is unknown
  (old device had no SSH access for the agent).

---

## 3. Milestone history (all E2E-verified unless noted)

| Tag | Milestone |
|---|---|
| `orders-v0.1.0` | Scaffold: 6-service hexagonal architecture, REST→Oracle→outbox→Kafka→consumers |
| `orders-v0.2.0` | product-service: Oracle + Redis cache-aside, evict-on-write, TTL |
| `orders-v0.3.0` | payment-service: Strategy pattern (card/wallet/bank), idempotency, CREATED→PAID flow |
| `orders-v0.4.0` | JWT/OAuth2: auth-service RS256+JWKS, Spring Cloud Gateway, role matrix, defense in depth |
| `orders-v0.5.0` | Idempotent consumers: transactional inbox (order), Redis SETNX dedupe (notification) |
| `orders-v0.6.0` | Resilience4j retry + circuit breaker, authoritative pricing, fail-open/closed |
| `orders-v0.7.0` | Observability: Prometheus + Grafana dashboard + Micrometer Tracing/OTel → Jaeger |
| (untagged) | **K8s Helm chart — WRITTEN, LINTED, NOT DEPLOYED** (see §5) |

Services: order(8080), product(8081), payment(8082), notification(8083),
auth(8084), gateway(9000). Infra: Oracle 23ai Free, Kafka KRaft, Redis,
Prometheus(9090), Grafana(3000, admin/admin), Jaeger(16686), OTel collector.

---

## 4. Demo facts (memorize these)

- **Login:** `POST /auth/login` `{"username":"alice","password":"password"}`
  → `{"accessToken":...}`. Users: alice=CUSTOMER, bob=ADMIN, carol=both.
- **Order POST** (through gateway :9000, route `/api/v1/orders` — NOT `/api/orders`):
  ```json
  {"customerId":"11111111-1111-1111-1111-111111111111",
   "lines":[{"productId":"22222222-2222-2222-2222-222222222222","quantity":2,"unitPrice":99.99}]}
  ```
  (NOT flat productId/quantity — it's a `lines` array.)
- **Seed product** `22222222-...` = Mechanical Keyboard Pro, 99.99 (the
  authoritative price that overrides client-sent prices).
  `33333333-...` = USB-C Docking Station 149.5 (EXISTS — don't use for
  unknown-product tests). Use `99999999-...` for unknown-product 400 tests.
- **Customer ID:** `11111111-1111-1111-1111-111111111111`.

---

## 5. CURRENT TASK: finish the Kubernetes milestone (v0.8.0)

**State:** Helm chart complete at `deploy/helm/order-platform/` —
`helm lint` + `helm template` clean on the old device. Renders: Namespace,
ServiceAccount, Secret (DB password), 6× Deployment+NodePort Service
(readiness/liveness probes on `/actuator/health`, resources 100m/512Mi →
500m/768Mi, Prometheus scrape annotations), HPA on order-service (2→5 @ 60% CPU).

**Why it stalled:** Docker Desktop's Kubernetes failed to bootstrap on the
old device — `kind.log` showed `wait-control-plane` timing out
(`dial tcp 172.19.0.2:6443: connection refused`), Docker Desktop deleted the
node and never retried; disable/re-enable didn't fix it. User moved devices.

**Architecture decision (already documented):** only the 6 app services run
in K8s; Oracle/Kafka/Redis/observability stay on `docker compose`, reached
via `host.docker.internal`. `imagePullPolicy: Never` (images from local
Docker daemon, no registry).

**To finish (also in deploy/helm/order-platform/README.md "Resume / first deploy"):**
1. Confirm cluster: `kubectl get nodes` (docker-desktop Ready).
2. `docker compose build` + `docker compose up -d oracle kafka redis prometheus grafana otel-collector jaeger`
3. `helm upgrade --install demo deploy/helm/order-platform`
4. `kubectl get pods -n order-platform -w` → all Ready.
5. E2E: login + create order through **localhost:30000** (gateway NodePort;
   others: order 30080, product 30081, payment 30082, notification 30083,
   auth 30084). Expect 201 with authoritative price 99.99.
6. Verify HPA: `kubectl get hpa -n order-platform` (metrics take 1–2 min).
7. Write `docs/e2e/e2e-v0.8.0.md` (use e2e-v0.7.0.md as format template),
   update `docs/e2e/README.md` index, progress.md (done items + tag table
   row), README (roadmap checkbox), remove the "unverified" banner from the
   deploy README, commit, ff-merge, tag `orders-v0.8.0`, remind user to push.

**Possible issues to expect:**
- `host.docker.internal` DNS from pods (should work on Docker Desktop K8s;
  if not, values.yaml `infra.*` → node IP).
- Kafka: pods must use `host.docker.internal:9092` (EXTERNAL listener), NOT
  `kafka:29092` (compose-network-only hostname).
- Flyway races if order/product boot simultaneously against shared schema —
  history tables are per-service, should be fine.
- HPA shows `<unknown>` until metrics-server's first poll (~1–2 min).

---

## 6. Backlog after v0.8.0 (priority order, from progress.md)

1. k6/Gatling load test + SQL EXPLAIN PLAN before/after case study
2. PIT mutation testing run
3. ADRs (why Kafka over RabbitMQ, why outbox pattern)
4. Demo GIF/video for README
5. "Known limitations / next steps" refresh before publishing (recruiter prep)

---

## 7. Environment gotchas (old device — Windows; likely still relevant)

- **PowerShell 5.x**: NO `&&`/`||`/`??`. Chain with `;`, gate with
  `if ($?) { ... }`.
- **curl.exe + long JWT inline** → mangled headers → mysterious 403
  `insufficient_scope`. Use `Invoke-RestMethod`, or write the header to a
  file and `curl -H "@file"`. Git Bash curl is fine.
- **`localhost` may resolve to IPv6** where WSL's `wslrelay.exe` squats
  (old device: `[::1]:8080`). Use `127.0.0.1` explicitly.
- **`Out-File` writes UTF-16 BOM** in PS 5.x — use
  `[System.IO.File]::WriteAllText()` for ASCII files.
- **`docker compose up -d <svc>` restarts its dependencies** — after
  fail-open tests, re-stop product-service.
- **product-service takes ~25s** to fully start; circuit-breaker half-open
  probes need ~40s wait or they fail and re-open the circuit.
- **CircuitBreakerRegistry.ofDefaults()** has minimumNumberOfCalls=100 —
  tests must pass explicit `CircuitBreakerConfig`.
- **Jaeger API timestamps are microseconds** (not seconds/ms).
- **Grafana `${DS_PROMETHEUS}` templating doesn't resolve via file
  provisioning** — dashboard pins datasource UID `PBFA97CFB590B2093`
  directly (set in the datasource provisioning yml).
- **`RestClient.builder()` (raw) is NOT tracing-instrumented** — inject the
  auto-configured `RestClient.Builder` instead (fixed in
  ProductCatalogClient in v0.7.0; great interview story).
- **Maven:** `mvn '-pl' 'services/order-service' 'test'` for targeted runs;
  full reactor for milestones. Java 21, Spring Boot 3.3.4.
- **Helm on new device:** `winget install Helm.Helm` (package id is
  `Helm.Helm`, NOT `Kubernetes.helm`). PATH may need a shell restart.

---

## 8. Session workflow conventions

- Todos are tracked in the session SQL DB (`todos` table) — recreate them
  per milestone (see prior pattern: obs-*, k8s-*).
- Checkpoints are auto-saved; checkpoint titles describe milestone state.
- The user says "continue to the next backlog" to advance — always consult
  `docs/progress.md` "Next up" section for what's next.
- Ask the user before: installing anything on their device, design decisions
  with multiple good options (they like being consulted, e.g. chose
  Micrometer Tracing over raw OTel SDK).
- The user is friendly, calls the project "our journey", and appreciates
  concise tables + clear "your action" callouts when something needs them
  (pushing, clicking Docker settings, etc.).
