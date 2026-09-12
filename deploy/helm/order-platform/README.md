# Kubernetes Deployment (Helm)

> **Status: deployed and E2E verified on minikube** (tag `orders-v0.8.0`) —
> full lifecycle (login ? order ? payment ? PAID) through the in-cluster
> gateway, HPA live metrics, Prometheus 7/7 targets, Jaeger traces from all
> services. Reproduction guide: [docs/e2e/e2e-v0.8.0.md](../../../docs/e2e/e2e-v0.8.0.md).

Deploys the 6 Spring Boot services to a local Kubernetes cluster with
startup/liveness/readiness probes, resource requests/limits, and an HPA on
order-service.

**Architecture note:** only the *application* services run in Kubernetes.
Stateful infrastructure (Oracle, Kafka, Redis, Prometheus/Grafana/Jaeger)
keeps running via `docker compose` on the host — a pragmatic demo split
that mirrors real-world patterns where managed services (ATP, MSK, ElastiCache)
live outside the cluster.

```
+- Kubernetes (minikube, docker driver) -----+   +- docker compose (host) -+
¦  api-gateway --+                           ¦   ¦  Oracle :1521           ¦
¦  order (x2+HPA)¦  product  payment         ¦--?¦  Kafka  :9092 (external)¦
¦  notification  ¦  auth                     ¦   ¦  Redis  :6379           ¦
+----------------+---------------------------+   ¦  Prometheus/Grafana/    ¦
                 ¦ host.minikube.internal         ¦  Jaeger/OTel           ¦
                 +------------------------------?+-------------------------+
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
docker compose build
minikube image load order-platform/api-gateway order-platform/auth-service `
    order-platform/order-service order-platform/product-service `
    order-platform/payment-service order-platform/notification-service

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

The release installs into the `default` namespace (release metadata) but
deploys workloads into `order-platform` (set via `values.yaml`
`global.namespace`).

## Verify

```powershell
kubectl get pods,svc,hpa -n order-platform
# expect: all pods Running/Ready, 6 NodePort services, demo-order HPA

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

## Chart structure

```
deploy/helm/order-platform/
+-- Chart.yaml
+-- values.yaml          # replicas, resources, HPA targets, infra hosts
+-- templates/
    +-- _helpers.tpl
    +-- namespace.yaml   # namespace + service account
    +-- secret.yaml      # DB password (demo-grade; production: external secrets)
    +-- service.yaml     # generic Deployment+Service loop over all 6 services
    +-- hpa.yaml         # order-service CPU autoscaling 2?3 @ 70%
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

## Gotchas

- **`host.docker.internal` is unreachable from minikube pods** — it
  resolves, but ports don't answer. Use `host.minikube.internal`
  (192.168.65.254) for all host infra; it's already set in `values.yaml`.
- **Kafka advertised listeners**: compose must run with
  `KAFKA_EXTERNAL_ADVERTISED_HOST=host.minikube.internal`, otherwise
  bootstrap succeeds but the broker hands out `localhost:9092` and every
  produce/consume from a pod dead-ends on the pod's own localhost.
- **Boot-storm crash loops on small nodes**: with HPA `minReplicas=2`, a
  restart boots multiple JVMs at once; the CPU spike makes HPA scale up,
  the 8GB node saturates (load 60+, swap full), the node goes NotReady and
  everything restarts — a feedback loop. To break an active loop:
  `kubectl scale deployment/demo-order -n order-platform --replicas=0`,
  wait for node load ~1, scale back to 2.
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
