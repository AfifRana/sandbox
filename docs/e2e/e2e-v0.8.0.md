# E2E — orders-v0.8.0: Kubernetes Deployment (minikube + Helm)

Reproduces the Kubernetes milestone: all 6 Spring Boot services deployed to
minikube via the Helm chart, with infra (Oracle, Kafka, Redis, observability)
still running in `docker compose` on the host. Probes, resource limits, HPA
autoscaling, Prometheus scraping, and the full order lifecycle (CREATED→PAID)
verified through the cluster.

| Component | Role | Where |
|---|---|---|
| minikube (docker driver) | Single-node K8s cluster | `192.168.49.2` |
| Helm chart `order-platform` | Deploys 6 services + NodePorts + HPA | release `demo`, namespace `order-platform` |
| docker compose | Infra only: Oracle, Kafka, Redis, Prometheus, Grafana, OTel, Jaeger | host, `host.minikube.internal` from pods |
| kubectl port-forward | Windows host → gateway (no route to 192.168.49.2) | `localhost:30000` |

## Pre-conditions

- minikube running: `minikube start` (docker driver, 8GB node)
- metrics-server addon enabled: `minikube addons enable metrics-server`
  (required for HPA — without it the HPA shows `<unknown>` and never scales)
- Infra containers up **with the K8s-mode env vars** (same shell):

  ```powershell
  $env:PROMETHEUS_CONFIG='prometheus-k8s.yml'          # scrape NodePorts 192.168.49.2
  $env:KAFKA_EXTERNAL_ADVERTISED_HOST='host.minikube.internal'  # pods can reach advertised listener
  docker compose -f docker-compose.yml up -d oracle kafka redis prometheus grafana otel-collector jaeger
  ```

  ⚠️ Starting *only* infra services matters: the compose app services would
  bind host port 8080 and conflict with nothing in K8s, but they are
  redundant (apps run in the cluster) and waste ~3GB RAM on an 8GB node.
- Service images built and loaded into minikube:

  ```powershell
  docker compose build
  minikube image load order-platform/api-gateway order-platform/auth-service `
      order-platform/order-service order-platform/product-service `
      order-platform/payment-service order-platform/notification-service
  ```

  (Image names per `docker-compose.yml`; `minikube image load` survives
  container restarts, unlike `docker save | ctr import`.)
- Helm release installed:

  ```powershell
  helm upgrade --install demo deploy/helm/order-platform
  ```

## Steps

### 1. All pods Ready, HPA reporting

```powershell
kubectl get pods,hpa -n order-platform
# expect: 6+ pods Running/Ready (order x2), demo-order HPA with a CPU percentage
kubectl top pods -n order-platform   # metrics-server working
```

### 2. Port-forward the gateway

The Windows host cannot route to `192.168.49.2` (WSL NAT; a persistent route
needs an elevated `route add`). Port-forward instead:

```powershell
kubectl port-forward -n order-platform svc/demo-gateway 30000:8080
```

Keep this running in its own terminal; it dies whenever the gateway pod
restarts — restart it if calls suddenly refuse connections.

### 3. Full order lifecycle (login → order → payment → PAID)

```powershell
# Login
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:30000/auth/login' `
  -ContentType 'application/json' -Body '{"username":"alice","password":"password"}'
$hdr = @{ Authorization = "Bearer $($login.accessToken)" }

# Create order (server-authoritative price overrides the client's)
$order = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:30000/api/v1/orders' `
  -ContentType 'application/json' -Headers $hdr `
  -Body '{"customerId":"11111111-1111-1111-1111-111111111111","lines":[{"productId":"22222222-2222-2222-2222-222222222222","quantity":2,"unitPrice":99.99}]}'
$order.status        # CREATED
$order.totalAmount   # 179.98 (authoritative unitPrice 89.99 x 2)

# Pay (synchronous REST → outbox → Kafka payment-events → order-service consumer)
$pay = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:30000/api/v1/payments' `
  -ContentType 'application/json' -Headers $hdr `
  -Body (@{ orderId=$order.id; customerId=$order.customerId; amount=$order.totalAmount; method='CARD' } | ConvertTo-Json)
$pay.status          # COMPLETED

# Verify the asynchronous transition
Start-Sleep 5
$got = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:30000/api/v1/orders/$($order.id)" -Headers $hdr
$got.status          # PAID
```

### 4. Kafka event flow verified in logs

```powershell
kubectl logs -n order-platform deployment/demo-notification --tail=50 | Select-String "Notifying customer"
# expect: Notifying customer about order <orderId> (CREATED)
```

This is the end-to-end proof of the Kafka advertised-listener fix: the
notification consumer (in a pod) bootstraps via `host.minikube.internal:9092`
and receives the `order-events` message published by order-service.

### 5. Prometheus scrapes the cluster

Prometheus runs on the host with `prometheus-k8s.yml`, scraping the service
NodePorts on `192.168.49.2`:

```powershell
(Invoke-RestMethod 'http://127.0.0.1:9090/api/v1/targets').data.activeTargets |
  ForEach-Object { "$($_.labels.job)  $($_.health)" }
# expect: 6 services + prometheus -> all up
```

### 6. Traces reach Jaeger through OTLP

```powershell
(Invoke-RestMethod 'http://127.0.0.1:16686/api/services').data
# expect: all 6 service names listed
```

## Post-conditions

- All pods `Running`/`Ready` in `order-platform`; no crash loops
- HPA shows live CPU utilization (not `<unknown>`) and respects
  `min=2 max=3 @70%`
- Order lifecycle CREATED→PAID works through the cluster gateway
- Prometheus: 7/7 targets `up`
- Jaeger: traces from all 6 services

## Gotchas

- **Boot-storm crash loops on small nodes**: with HPA `minReplicas=2`, a
  rolling restart boots multiple JVMs simultaneously. The CPU spike makes
  HPA scale up (booting JVMs eat CPU), the 8GB WSL node saturates (load
  60+, swap full), the node goes NotReady, and everything restarts — a
  feedback loop. Mitigations: HPA capped at `maxReplicas=3` and CPU target
  raised to 70% (see `values.yaml`); to break an active loop, scale order
  to 0 (`kubectl scale deployment/demo-order -n order-platform --replicas=0`),
  wait for node load ~1, then scale back to 2.
- **`helm upgrade --reuse-values` trap**: it reuses the *previous release's*
  values, overriding changed chart defaults. After editing `values.yaml`,
  run `helm upgrade demo deploy/helm/order-platform` **without** the flag
  (or with `-f values.yaml`).
- **Host can't reach the NodePort IP**: WSL NAT means Windows has no route
  to `192.168.49.2`. Use `kubectl port-forward`; `route add` requires an
  elevated shell.
- **minikube container restart changes the apiserver port**: after
  `docker restart minikube` (or a host reboot), `kubectl` fails with
  `connection refused` on the old port — run `minikube update-context`,
  and `minikube start` if the container is Stopped.
- **`host.docker.internal` is unreachable from minikube pods** — it
  resolves, but the ports don't answer. Use `host.minikube.internal`
  (192.168.65.254) for all host infra in `values.yaml`.
- **Kafka advertised listeners**: compose must run with
  `KAFKA_EXTERNAL_ADVERTISED_HOST=host.minikube.internal`, otherwise
  bootstrap succeeds but the broker hands out `localhost:9092` and every
  produce/consume from a pod dead-ends on the pod's own localhost.
- **Env vars don't persist between PowerShell sessions** — set
  `PROMETHEUS_CONFIG` and `KAFKA_EXTERNAL_ADVERTISED_HOST` in the same
  command as `docker compose up`.
- **Compose app services conflict**: `docker compose up -d` without service
  names also starts the app containers (port 8080, ~3GB RAM) — start only
  the infra services.
- **Startup probes are generous on purpose**: JVM boot under CPU contention
  takes 2+ minutes (measured 133s for auth-service); the startup probe
  (30×10s = 300s budget) tolerates it, liveness only kicks in after startup
  succeeds.
- **PowerShell**: use `Invoke-RestMethod` for JWT-bearing calls (see
  v0.6.0 gotchas).

## Interview talking points

- **Apps in cluster, infra on host** mirrors the real-world split (EKS pods
  + RDS/MSK/ElastiCache): the chart only owns stateless workloads, secrets
  and config wire in via env vars, and infra lifecycle stays independent.
- **HPA trade-offs on tiny nodes**: autoscaling is only as good as the
  metrics pipeline (metrics-server) and the headroom underneath it — on a
  saturated 8GB node, scaling *up* can make things worse; production
  clusters need buffer for boot spikes or KEDA-style scaling on queue lag.
- **`imagePullPolicy: Never` + `minikube image load`** is the no-registry
  local loop; production uses a real registry with `IfNotPresent`.
- **Readiness vs liveness vs startup**: three probes, three jobs — readiness
  keeps booting pods out of Service endpoints, startup gives slow JVMs a
  300s grace instead of liveness kills, liveness restarts wedged containers.
- **Port-forward vs NodePort vs Ingress**: NodePorts serve the scrape path
  (Prometheus on the docker network reaches 192.168.49.2 directly), while
  the interactive path from Windows is port-forward; production would use
  an Ingress/LoadBalancer in front.
