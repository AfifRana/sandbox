# E2E — orders-v0.9.0: k6 Load Test + SQL EXPLAIN PLAN

Reproduces the performance milestone: the customer order-list query is
measured with k6, explained before and after a composite Oracle index, and
measured again after Flyway applies the migration.

| Component | Role | Where |
|---|---|---|
| k6 | HTTP load generator and p95/p99 measurements | host, `load-tests/k6/baseline.js` |
| Oracle 23ai Free | Orders table and execution plans | Docker Compose, `FREEPDB1` |
| order-service | `GET /api/v1/orders?customerId=...` query under test | minikube |
| composite index | Covers customer filter + newest-first ordering | Flyway V3 |

## Pre-conditions

- k6 is installed and on `PATH` (tested with k6 v0.49.0).
- Minikube is running with the order-platform release deployed and all
  order-service pods ready:

  ```powershell
  kubectl get pods -n order-platform
  # expect: demo-order pods Running/Ready
  ```

- Oracle is running in the Compose project:

  ```powershell
  docker compose ps oracle
  # expect: oracle Up (healthy)
  ```

- The gateway is port-forwarded in a separate terminal. The Windows host
  cannot route directly to the minikube NodePort:

  ```powershell
  kubectl port-forward -n order-platform svc/demo-gateway 30000:8080
  ```

- Run all commands below from the repository root. The scripts use the
  canonical customer `11111111-1111-1111-1111-111111111111`.

## Steps

### 1. Seed a repeatable query data set

The seed script defaults to 10,000 orders for the queried customer and
50,000 orders for a second customer. This keeps the customer predicate
selective enough to compare the original and composite index paths.

```powershell
Get-Content docs/perf/seed_orders.sql |
  docker exec -i order-processing-platform-oracle-1 sqlplus -s orders/orders@FREEPDB1
```

Verify the rows exist:

```powershell
$sql = @'
SELECT COUNT(*) FROM orders
WHERE customer_id = HEXTORAW('11111111111111111111111111111111');
EXIT
'@
$sql | docker exec -i order-processing-platform-oracle-1 sqlplus -s orders/orders@FREEPDB1
# expect: the seeded row count (plus any earlier demo orders)
```

### 2. Capture the pre-index execution plan

```powershell
$sql = @'
SET PAGESIZE 100
SET LINESIZE 200
EXPLAIN PLAN FOR
SELECT id, customer_id, status, total_amount, created_at
FROM orders
WHERE customer_id = HEXTORAW('11111111111111111111111111111111')
ORDER BY created_at DESC;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY());
EXIT
'@
$sql | docker exec -i order-processing-platform-oracle-1 sqlplus -s orders/orders@FREEPDB1 |
  Tee-Object docs/perf/explain-before.txt
```

Before V3, the plan should show the existing customer index plus a sort
operation, or another plan with an explicit ordering cost. Record the actual
plan rather than assuming a specific cost.

### 3. Run the k6 baseline

The default script logs in once during k6 setup and reads recent orders for
the canonical customer. This isolates the SQL query under test. Set
`K6_CREATE_ORDER=true` only when the create-order path should also be included.

```powershell
$env:GATEWAY_URL = 'http://127.0.0.1:30000'
$env:K6_VUS = '2'
$env:K6_DURATION = '30s'
k6 run load-tests/k6/baseline.js
```

Record the `http_req_duration` p95/p99 and `http_req_failed` values in the
milestone notes. The script uses `alice/password` by default and never
prints the access token. Override credentials with `K6_USERNAME` and
`K6_PASSWORD`; these names avoid collisions with host environment variables.

### 3a. Captured reference results

Using the default seed distribution, two VUs, and a 30-second run:

| State | Plan access path | Requests | p95 | p99 | Failed |
|---|---|---:|---:|---:|---:|
| Before V3 | `IDX_ORDERS_CUSTOMER_ID` plus `SORT ORDER BY STOPKEY` | 55 | 199.05 ms | 257.80 ms | 0% |
| After V3 | `IDX_ORDERS_CUSTOMER_CREATED_AT` plus `SORT ORDER BY STOPKEY` | 49 | 369.26 ms | 3.90 s | 0% |

The captured raw outputs are `docs/perf/k6-before.txt`,
`docs/perf/k6-after.txt`, `docs/perf/explain-before.txt`, and
`docs/perf/explain-after.txt`. The repeated run did not demonstrate a
speedup: Oracle retained a sort operation and the after-run p95/p99 were
noisier. This is an intentional negative result; the case study reports the
optimizer's actual behavior rather than claiming an index improvement without
evidence.

### 4. Apply V3 and capture the post-index plan

Build and redeploy order-service so Flyway runs
`V3__add_customer_created_at_index.sql`. Use the normal image build/load and
Helm deployment procedure for the current Kubernetes environment, then wait
for the order-service readiness probe to pass.

Confirm the index exists:

```powershell
$sql = @'
SELECT index_name, column_name, column_position, descend
FROM user_ind_columns
WHERE index_name = 'IDX_ORDERS_CUSTOMER_CREATED_AT'
ORDER BY column_position;
EXIT
'@
$sql | docker exec -i order-processing-platform-oracle-1 sqlplus -s orders/orders@FREEPDB1
# expect: CUSTOMER_ID ASC, CREATED_AT DESC
```

Repeat step 2, writing to `docs/perf/explain-after.txt`. The post-index plan
should be compared using the actual cost, cardinality, and sort operations.

### 5. Run the post-index k6 measurement

```powershell
k6 run load-tests/k6/baseline.js
```

Compare the same VU/duration settings with the baseline. Do not call the
milestone complete until both the SQL plan and real HTTP measurements are
captured.

## Post-conditions

- `GET /api/v1/orders?customerId=...` returns HTTP 200 through the gateway.
- `IDX_ORDERS_CUSTOMER_CREATED_AT` exists with `CUSTOMER_ID` followed by
  `CREATED_AT DESC`.
- `docs/perf/explain-before.txt` and `docs/perf/explain-after.txt` contain
  the real Oracle plans.
- Both k6 runs report comparable p95/p99 and failure-rate values.
- No `.._*` mangled Maven folders are created in the worktree.

## Gotchas

- Port-forwards die when pods restart; restart the port-forward before
  rerunning the HTTP steps.
- Oracle stores UUID values as `RAW(16)`; the SQL uses the 32-character
  hexadecimal form, while the REST API uses the hyphenated UUID form.
- Flyway runs V3 only after order-service starts against the same Oracle
  schema. Check the service logs if the index is missing.
- Do not infer an improvement from the index definition alone. Oracle may
  choose a different plan based on table statistics and data volume; retain
  both actual plans and k6 outputs.
- On the 8 GB minikube node, avoid restarting all JVM deployments at once.
  A rolling image update can trigger the documented boot-storm behavior.
- Use `Invoke-RestMethod` for JWT-bearing PowerShell calls; the k6 script
  handles its own token header.

## Interview talking points

- A single-column customer index filters rows but leaves the ordering to a
  separate sort; the composite index gives Oracle an access path containing
  both customer and timestamp columns. The optimizer may still retain the
  sort, so the plan and measured latency must be checked.
- `EXPLAIN PLAN` validates the optimizer's chosen access path, while k6
  validates the user-visible API latency under concurrent traffic.
- The migration is versioned by Flyway, so the schema optimization is
  repeatable and travels with the service deployment.
