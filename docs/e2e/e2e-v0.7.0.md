# E2E — orders-v0.7.0: Observability (Prometheus + Grafana + OTel Tracing)

Reproduces the observability milestone: every service exposes Prometheus
metrics, a provisioned Grafana dashboard visualizes platform health, and
distributed traces flow gateway → order → product through an OpenTelemetry
collector into Jaeger.

| Component | Role | Where |
|---|---|---|
| Micrometer + Prometheus registry | Metrics per service (`/actuator/prometheus`) | all 6 services |
| Prometheus | Scrapes all services on the internal Docker network (5s) | http://localhost:9090 |
| Grafana | Provisioned datasource + 8-panel dashboard | http://localhost:3000 (admin/admin) |
| Micrometer Tracing (OTel bridge) | W3C `traceparent` propagation, 100% sampling | all 6 services |
| OTel Collector | Batches spans, forwards to Jaeger | internal :4318 |
| Jaeger | Trace storage + UI | http://localhost:16686 |

## Pre-conditions

- Full stack running: `docker compose up -d --build` (13 containers —
  the 6 services plus kafka, oracle, redis, prometheus, grafana,
  otel-collector, jaeger)
- `docker compose ps` shows all containers `Up`, Oracle `(healthy)`
- Wait ~60s after startup: services register with Prometheus on first
  scrape and the first traces only appear once traffic flows

## Steps

### 1. Prometheus targets all UP

```bash
curl -s http://localhost:9090/api/v1/targets | jq -r '.data.activeTargets[] | "\(.labels.job) -> \(.health)"'
# expect: all 7 jobs (6 services + prometheus itself) -> up
```

### 2. Generate traffic

```bash
ALICE=$(curl -s -X POST http://localhost:9000/auth/login -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

for i in 1 2 3 4 5; do
  curl -s -o /dev/null -X POST http://localhost:9000/api/v1/orders \
    -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
    -d '{"customerId":"11111111-1111-1111-1111-111111111111","lines":[{"productId":"22222222-2222-2222-2222-222222222222","quantity":2,"unitPrice":99.99}]}'
done
# expect: 5x HTTP 201
```

### 3. Metrics are queryable in Prometheus

```bash
# Circuit breaker state (1 = closed)
curl -s "http://localhost:9090/api/v1/query?query=resilience4j_circuitbreaker_state" | jq -r '.data.result[].metric | "\(.job) \(.name) \(.state)"'

# HTTP request counters per status
curl -s --get "http://localhost:9090/api/v1/query" \
  --data-urlencode 'query=http_server_requests_seconds_count{job="order-service",uri="/api/v1/orders"}' \
  | jq -r '.data.result[] | "\(.metric.method) \(.metric.status) count=\(.value[1])"'
# expect: POST 201 count=5 (plus any earlier traffic)
```

### 4. Grafana dashboard renders

Open http://localhost:3000 (admin/admin) → Dashboards → **Order Platform —
Services Overview**. All 8 panels resolve against the provisioned
Prometheus datasource (UID `PBFA97CFB590B2093`):

- HTTP request rate (all services) — RED "rate"
- HTTP p95 latency (order-service) — RED "duration"
- JVM memory used (heap)
- Circuit breaker state (order-service → productCatalog)
- Circuit breaker calls (failure vs not-permitted)
- Kafka consumer lag (notification-service)
- System CPU usage (all services)
- HikariCP active connections

### 5. Cross-service trace in Jaeger

Open http://localhost:16686 → Service = `product-service` → Find Traces.
Pick a trace whose root operation is `http post`; it should contain spans
from **three services** under one trace ID:

```
[api-gateway]      http post /api/v1/orders
[order-service]    http post /api/v1/orders
[order-service]    http get  http://product-service:8080/api/v1/products/22222222-...
[product-service]  http get  /api/v1/products/{id}
```

Or via API:

```bash
END=$(date +%s)000000; START=$((END - 600000000))
curl -s "http://localhost:16686/api/traces?service=product-service&limit=5&start=$START&end=$END" \
  | jq -r '.data[] | .traceID as $t | .spans[] | "\($t[0:16]) [\(.processID)] \(.operationName)"' | head -20
```

Also visible: trace IDs in order-service logs (`docker compose logs
order-service` shows `[<traceId>-<spanId>]` in the log pattern) — the same
trace ID that appears in Jaeger, which is the whole point of correlating
logs and traces.

## Post-conditions

- All 7 Prometheus targets report `up`
- `resilience4j_circuitbreaker_state{state="closed"} = 1` for `productCatalog`
- Grafana dashboard shows live HTTP rate/latency for all 6 services
- A single trace ID spans api-gateway → order-service → product-service
- Trace IDs appear in order-service log lines

## Gotchas

- **First scrape delay**: a freshly started service shows `down` in
  Prometheus until its next scrape (~5s) *and* the app is fully listening —
  order-service can take ~40s total. Re-query after a minute before
  assuming a problem.
- **`RestClient.builder()` vs the injected `RestClient.Builder`**: a raw
  `RestClient.builder()` is NOT instrumented — no `traceparent` header, so
  the downstream span starts a new trace. Always inject the auto-configured
  `RestClient.Builder` (fixed in `ProductCatalogClient` this milestone).
- **Grafana dashboard datasource templating**: `${DS_PROMETHEUS}` variables
  only resolve when importing through the UI, not via file provisioning.
  The dashboard pins the provisioned datasource UID directly.
- **Jaeger API timestamps are microseconds** — `date +%s` seconds will be
  rejected with a 400 parse error.
- **PowerShell**: use `Invoke-RestMethod` for anything carrying a JWT;
  curl.exe + long inline tokens gets mangled (see v0.6.0 gotchas).

## Interview talking points

- **Pull vs push**: Prometheus pulls (`/actuator/prometheus`) — the platform
  survives a monitoring outage because apps don't block on metrics pushes;
  scraping happens on the internal Docker network, never through the public
  gateway.
- **Why an OTel Collector between apps and Jaeger**: apps only know OTLP;
  the collector owns batching, retries, and vendor routing. Swapping Jaeger
  for Tempo/Zipkin is a collector config change, not a redeploy of 6 services.
- **Micrometer Tracing vs raw OTel SDK**: the bridge gives Spring-native
  `@Observed`/RestClient instrumentation and W3C trace context without
  managing OTel SDK internals — less code, same standard.
- **RED + USE**: the dashboard covers Rate/Errors/Duration (HTTP panels) and
  Utilization/Saturation/Errors (JVM heap, CPU, HikariCP, Kafka lag) — the
  two standard signal frameworks for service + resource health.
- **Trace-log correlation**: the logback pattern includes the trace ID, so a
  support ticket with a log line can be pasted straight into Jaeger.
- **Sampling**: 100% for the demo; production would sample (e.g. 10%) plus
  tail-based sampling in the collector to keep rare-error traces.
