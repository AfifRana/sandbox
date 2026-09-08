# Kubernetes Deployment (Helm)

> **Status: chart written and `helm lint`/`helm template` verified — NOT yet
> deployed to a live cluster.** Docker Desktop's Kubernetes failed to
> bootstrap on the device this was authored on (`kind.log` showed
> `wait-control-plane` timing out against `172.19.0.2:6443`, then Docker
> Desktop deleted the node and did not retry). A Kubernetes-disable/enable
> cycle did not fix it. **Resume here on a new device** — see "Resume /
> first deploy" below. Do not tag `orders-v0.8.0` until an E2E run through
> the NodePorts below has actually succeeded.

Deploys the 6 Spring Boot services to a local Kubernetes cluster with
liveness/readiness probes, resource requests/limits, and an HPA on
order-service.

**Architecture note:** only the *application* services run in Kubernetes.
Stateful infrastructure (Oracle, Kafka, Redis, Prometheus/Grafana/Jaeger)
keeps running via `docker compose` on the host — a pragmatic demo split
that mirrors real-world patterns where managed services (ATP, MSK, ElastiCache)
live outside the cluster.

```
┌─ Kubernetes (Docker Desktop) ──────────────┐   ┌─ docker compose (host) ─┐
│  api-gateway ──┐                           │   │  Oracle :1521           │
│  order (x2+HPA)│  product  payment         │──▶│  Kafka  :9092 (external)│
│  notification  │  auth                     │   │  Redis  :6379           │
└────────────────┼───────────────────────────┘   │  Prometheus/Grafana/    │
                 │ host.docker.internal           │  Jaeger/OTel           │
                 └──────────────────────────────▶└─────────────────────────┘
```

## Prerequisites (tooling this adds to your machine)

| Tool | Install | Needed for | Already present? |
|---|---|---|---|
| Docker Desktop **with Kubernetes enabled** | Docker Desktop → Settings → Kubernetes → Enable Kubernetes → Apply | The cluster itself (runs inside Docker Desktop's VM, not on Windows) | kubectl binary ships with Docker Desktop; the **cluster is off by default** |
| Helm 4.x | `winget install Helm.Helm` | Templating + installing this chart (plain CLI, no daemon) | Installed via winget during this milestone |
| kubectl 1.3x | `winget install Kubernetes.kubectl` *or* Docker Desktop's bundled copy | Talking to the cluster | ✅ bundled at `C:\Program Files\Docker\Docker\resources\bin\kubectl.exe` |
| metrics-server | bundled in Docker Desktop's K8s | HPA CPU metrics | ✅ ships enabled |

**Clean-up if you want your machine back:**
`winget uninstall Helm.Helm` + uncheck Kubernetes in Docker Desktop.

## Resume / first deploy (on a fresh device)

1. Install Docker Desktop, enable Kubernetes (Settings → Kubernetes →
   Enable Kubernetes → Apply & Restart), confirm with:
   ```bash
   kubectl get nodes    # expect: docker-desktop   Ready
   ```
   If the control plane fails to start (check
   `%LOCALAPPDATA%\Docker\log\host\kind.log` on Windows for
   `wait-control-plane` errors), try Settings → Kubernetes →
   **Reset Kubernetes Cluster**, or fully quit and restart Docker Desktop
   before re-enabling. This chart has not yet been deployed anywhere — you
   will be the first to run it end-to-end.
2. `winget install Helm.Helm` (adds `helm.exe`; restart the shell so PATH
   picks it up, or add
   `%LOCALAPPDATA%\Microsoft\WinGet\Packages\Helm.Helm_Microsoft.Winget.Source_8wekyb3d8bbwe\windows-amd64`
   to PATH manually)
3. Continue with **Deploy** below. Once the E2E in **Verify** succeeds,
   update this file's status line, check off the Kubernetes item in
   [docs/progress.md](../../../docs/progress.md), and tag `orders-v0.8.0`.

## Deploy

```bash
# 1. Build images (visible to the cluster via Docker Desktop's shared daemon)
docker compose build

# 2. Start infra on the host (Oracle, Kafka, Redis, observability)
docker compose up -d oracle kafka redis prometheus grafana otel-collector jaeger

# 3. Wait for Oracle to be healthy, then install the chart
kubectl config use-context docker-desktop
helm upgrade --install demo deploy/helm/order-platform

# 4. Watch pods become Ready (readiness probe gates traffic)
kubectl get pods -n order-platform -w
```

## Verify

```bash
kubectl get pods,svc,hpa -n order-platform
# expect: all pods Running/Ready, 6 NodePort services, demo-order HPA

# E2E through the gateway NodePort (localhost:30000 → api-gateway:8080)
TOKEN=$(curl -s -X POST http://localhost:30000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -s -X POST http://localhost:30000/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","lines":[{"productId":"22222222-2222-2222-2222-222222222222","quantity":2,"unitPrice":99.99}]}'
# expect: 201 with server-authoritative unitPrice 99.99
```

### NodePorts

| Service | NodePort | Maps to |
|---|---|---|
| api-gateway | 30000 | localhost:30000 |
| order-service | 30080 | localhost:30080 |
| product-service | 30081 | localhost:30081 |
| payment-service | 30082 | localhost:30082 |
| notification-service | 30083 | localhost:30083 |
| auth-service | 30084 | localhost:30084 |

## Chart structure

```
deploy/helm/order-platform/
├── Chart.yaml
├── values.yaml          # replicas, resources, HPA targets, infra hosts
└── templates/
    ├── _helpers.tpl
    ├── namespace.yaml   # namespace + service account
    ├── secret.yaml      # DB password (demo-grade; production: external secrets)
    ├── service.yaml     # generic Deployment+Service loop over all 6 services
    └── hpa.yaml         # order-service CPU autoscaling 2→5 @ 60%
```

## Design decisions

- **`imagePullPolicy: Never`** — images are built locally and shared from
  Docker Desktop's daemon; no registry push needed for the demo. CI would
  set this to `IfNotPresent` with a real registry.
- **Readiness vs liveness split**: readiness (20s delay, 5s period) keeps a
  starting pod out of Service endpoints until `/actuator/health` is green;
  liveness (40s delay) only restarts genuinely wedged containers — using
  liveness alone would cause restart storms during slow Oracle connects.
- **HPA on order-service only** — it is the write hotspot; the others are
  scaled by their fixed replica counts. `minReplicas: 2` also gives a
  rolling-update zero-downtime story.
- **Env-var wiring instead of ConfigMaps for app config** — the services
  already externalize all environment differences via `${ENV:default}`
  placeholders (12-factor); the chart just supplies cluster values.
- **DB password via Secret** (not plain env) — the baseline practice;
  production would use External Secrets Operator / Vault.

## Gotchas

- **`host.docker.internal` from inside the cluster**: pods reach host
  compose services through Docker Desktop's node alias. If DNS fails on a
  non-Docker-Desktop cluster, replace it in `values.yaml` (`infra.*`) with
  the node IP or a proper ExternalName Service.
- **Kafka advertised listeners**: compose exposes `EXTERNAL://localhost:9092`,
  which is exactly what pods need via `host.docker.internal:9092`. Do not
  point services at `kafka:29092` — that hostname only resolves inside the
  compose network.
- **First HPA metrics take ~1–2 min** — metrics-server polls on an interval;
  `kubectl describe hpa` shows `<unknown>` until then.
- **Flyway migrations run per-service on boot** — restarting a deployment
  is safe (history table + baselining), but two services sharing the schema
  both booting simultaneously can race; the chart starts them in parallel
  and Flyway's locking handles it (order-service owns its own tables).
