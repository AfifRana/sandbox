# Kubernetes Deployment (Helm)

> **Status: deployed and E2E verified on minikube.** The original six services
> were verified at tag `orders-v0.8.0`
> ([guide](../../../docs/e2e/e2e-v0.8.0.md)); the v0.11.0 saga trio
> (inventory, fulfillment, saga-orchestrator) was verified at tag
> `orders-v0.11.0` ([guide](../../../docs/e2e/e2e-v0.11.0.md)). Verified there:
> full lifecycle (login → order → payment → PAID) through the
> in-cluster gateway, HPA live metrics, Prometheus targets up, and Jaeger
> traces from the services.

Deploys the 9 Spring Boot services to a local Kubernetes cluster with
startup/liveness/readiness probes, resource requests/limits, and an HPA on
order-service.

**Architecture note:** only the *application* services run in Kubernetes.
Stateful infrastructure (Oracle, Kafka, Redis, Prometheus/Grafana/Jaeger)
keeps running via `docker compose` on the host — a pragmatic demo split
that mirrors real-world patterns where managed services (ATP, MSK, ElastiCache)
live outside the cluster.

The inventory, fulfillment, and saga-orchestrator services are
**Kubernetes-only**: they are deliberately absent from `docker-compose.yml`
(duplicating their consumers next to the Compose stack would fight over the
same topics), so their images come from an explicit `docker build` in step 1
below rather than `docker compose build`.

```
+- Kubernetes (minikube, docker driver) -----------------+   +- docker compose (host) ---+
|  api-gateway (30000)                                    |   |  Oracle      :1521        |
|  order (x2 + HPA)   product   payment                   |   |  Kafka       :9092 (ext)  |
|  notification       auth                                |<->|  Redis       :6379        |
|  inventory   fulfillment   saga-orchestrator            |   |  Prometheus  :9090        |
+---------------------------------------------------------+   |  Grafana     :3000        |
                      | host.minikube.internal                |  Jaeger/OTel :16686/:4318 |
                      +-------------------------------------->+---------------------------+
```

## Prerequisites (tooling this adds to your machine)

| Tool | Install | Needed for |
|---|---|---|
| minikube | `winget install Kubernetes.minikube` | The cluster (docker driver — reuses Docker Desktop's daemon, no extra VM) |
| Helm 4.x | `winget install Helm.Helm` | Templating + installing this chart |
| kubectl 1.3x | `winget install Kubernetes.kubectl` | Talking to the cluster |
| metrics-server | `minikube addons enable metrics-server` | HPA CPU metrics (not enabled by default on minikube) |

**Clean-up if you want your machine back:**
`minikube delete` + `winget uninstall Helm.Helm Kubernetes.minikube`.

## Deploy

```powershell
# 1. Build images and load them into minikube's daemon
#    (minikube's docker driver does NOT share the host daemon)
docker compose build        # the six Compose services

# the saga trio is Kubernetes-only, so build it explicitly (context = repo root)
docker build -t order-processing-platform-inventory-service:latest `
    -f services/inventory-service/Dockerfile .
docker build -t order-processing-platform-fulfillment-service:latest `
    -f services/fulfillment-service/Dockerfile .
docker build -t order-processing-platform-saga-orchestrator-service:latest `
    -f services/saga-orchestrator-service/Dockerfile .

minikube image load order-processing-platform-api-gateway `
    order-processing-platform-auth-service `
    order-processing-platform-order-service `
    order-processing-platform-product-service `
    order-processing-platform-payment-service `
    order-processing-platform-notification-service `
    order-processing-platform-inventory-service `
    order-processing-platform-fulfillment-service `
    order-processing-platform-saga-orchestrator-service

# 2. Start infra on the host with the K8s-mode env vars (same shell!)
$env:PROMETHEUS_CONFIG='prometheus-k8s.yml'
$env:KAFKA_EXTERNAL_ADVERTISED_HOST='host.minikube.internal'
docker compose up -d oracle kafka redis prometheus grafana otel-collector jaeger

# 3. Wait for Oracle to be healthy, then install the chart
minikube start                      # if not already running
minikube addons enable metrics-server
helm upgrade --install demo deploy/helm/order-platform

# 4. Watch pods become Ready (startup probe gives JVMs up to 300s)
kubectl get pods -n order-platform -w
```

Image names must match the chart's `image:` value in
`templates/service.yaml` (`order-processing-platform-<service>:latest`).
Because `imagePullPolicy: Never`, a name that is not loaded into minikube
fails the pod instead of pulling — there is no registry fallback.

The release installs into the `default` namespace (release metadata) but
deploys workloads into `order-platform` (set via `values.yaml`
`global.namespace`).

## Verify

```powershell
kubectl get pods,svc,hpa -n order-platform
# expect: all pods Running/Ready, 9 NodePort services, demo-order HPA

# The Windows host cannot route to the minikube IP (192.168.49.2, WSL NAT),
# so port-forward the gateway:
kubectl port-forward -n order-platform svc/demo-gateway 30000:8080

# Then in another shell (see docs/e2e/e2e-v0.8.0.md for the full flow):
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:30000/auth/login' `
  -ContentType 'application/json' -Body '{"username":"alice","password":"password"}'
# expect: accessToken in the response
```

### NodePorts

NodePorts serve the *scrape* path (host Prometheus reaches `192.168.49.2`
directly on the docker network). Interactive access from Windows uses
`kubectl port-forward` instead.

| Service | NodePort |
|---|---|
| api-gateway | 30000 |
| order-service | 30080 |
| product-service | 30081 |
| payment-service | 30082 |
| notification-service | 30083 |
| auth-service | 30084 |
| inventory-service | 30085 |
| fulfillment-service | 30086 |
| saga-orchestrator-service | 30087 |

## Chart structure

```
deploy/helm/order-platform/
+-- Chart.yaml
+-- values.yaml          # replicas, resources, HPA targets, infra hosts
+-- templates/
    +-- _helpers.tpl
    +-- namespace.yaml   # namespace + service account
    +-- secret.yaml      # DB password (demo-grade; production: external secrets)
    +-- service.yaml     # generic Deployment+Service loop over all 9 services
    +-- hpa.yaml         # order-service CPU autoscaling 2→3 @ 70%
```

## Design decisions

- **`imagePullPolicy: Never`** — images are loaded into minikube's daemon
  with `minikube image load`; no registry push needed for the demo. CI would
  set this to `IfNotPresent` with a real registry.
- **Startup probe (300s budget) + readiness + liveness**: JVM boot under CPU
  contention takes 2+ minutes on a small node; the startup probe tolerates
  it, readiness keeps a booting pod out of Service endpoints, and liveness
  (which only starts after the startup probe succeeds) restarts genuinely
  wedged containers — liveness alone caused restart storms during slow
  Oracle connects.
- **HPA on order-service only** — it is the write hotspot; the others are
  scaled by their fixed replica counts. `minReplicas: 2` also gives a
  rolling-update zero-downtime story. `maxReplicas: 3` and a 70% CPU target
  (not the usual 5 @ 60%) because this was verified on an 8GB WSL node where
  more booting JVMs saturate the node (see Gotchas).
- **Env-var wiring instead of ConfigMaps for app config** — the services
  already externalize all environment differences via `${ENV:default}`
  placeholders (12-factor); the chart just supplies cluster values.
- **DB password via Secret** (not plain env) — the baseline practice;
  production would use External Secrets Operator / Vault.
- **The saga trio runs only in Kubernetes** — inventory, fulfillment, and
  saga-orchestrator are not Compose services, so the chart is the single place
  that wires their datasource, broker, and JWKS URLs; each also needs
  `DB_URL`/`KAFKA_BROKERS` (already templated) to reach host infra.

## Gotchas

- **`host.docker.internal` is unreachable from minikube pods** — it
  resolves, but ports don't answer. Use `host.minikube.internal`
  (192.168.65.254) for all host infra; it's already set in `values.yaml`.
- **Kafka advertised listeners**: compose must run with
  `KAFKA_EXTERNAL_ADVERTISED_HOST=host.minikube.internal`, otherwise
  bootstrap succeeds but the broker hands out `localhost:9092` and every
  produce/consume from a pod dead-ends on the pod's own localhost. Sagas then
  stay `STARTED` forever even though every pod is Ready.
- **Boot-storm crash loops on small nodes**: with HPA `minReplicas=2`, a
  restart boots multiple JVMs at once; the CPU spike makes HPA scale up,
  the 8GB node saturates (load 60+, swap full), the node goes NotReady and
  everything restarts — a feedback loop. To break an active loop:
  `kubectl scale deployment/demo-order -n order-platform --replicas=0`,
  wait for node load ~1, scale back to 2.
- **Nine JVMs do not fit comfortably on the 8GB node**: for saga E2E, keep
  `demo-order` at 1 replica and scale idle services to 0
  (`kubectl scale deployment/demo-product demo-notification -n order-platform --replicas=0`);
  keep `demo-auth` up for logins.
- **`helm upgrade --reuse-values` trap**: it reuses the *previous release's*
  values, overriding changed chart defaults. After editing `values.yaml`,
  upgrade **without** the flag.
- **minikube container restart changes the apiserver port**: after a host
  reboot, `kubectl` fails with `connection refused` — run
  `minikube update-context` (and `minikube start` if the container is
  Stopped).
- **First HPA metrics take ~1–2 min** — metrics-server polls on an interval;
  `kubectl describe hpa` shows `<unknown>` until then.
- **Flyway migrations run per-service on boot** — restarting a deployment
  is safe (history table + baselining), but two services sharing the schema
  both booting simultaneously can race; the chart starts them in parallel
  and Flyway's locking handles it (order-service owns its own tables).