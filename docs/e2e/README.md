# End-to-End Verification Guides

Reproduction steps for every milestone's E2E run. Each guide is pinned to the
tag it verified — check out that tag first:

```bash
git checkout orders-v0.4.0
```

Guides describe **only what that milestone introduced**. Later milestones may
supersede earlier behavior (e.g. endpoints that now require auth); when that
happens the older guide is removed and its content folded into the newest one.

| Tag | Guide | Verified scope |
|---|---|---|
| `orders-v0.7.0` | [e2e-v0.7.0.md](e2e-v0.7.0.md) | Observability: Prometheus metrics, Grafana dashboard, OTel tracing → Jaeger |
| `orders-v0.6.0` | [e2e-v0.6.0.md](e2e-v0.6.0.md) | Resilience4j retry + circuit breaker, authoritative pricing, fail-open/fail-closed |
| `orders-v0.5.0` | [e2e-v0.5.0.md](e2e-v0.5.0.md) | Idempotent consumers: transactional inbox (order-service), Redis SETNX dedupe (notification-service) |
| `orders-v0.4.0` | [e2e-v0.4.0.md](e2e-v0.4.0.md) | JWT/OAuth2 security, gateway, role matrix, defense in depth |
| `orders-v0.3.0` | [e2e-v0.3.0.md](e2e-v0.3.0.md) | Payment Strategy pattern, idempotency, CREATED→PAID, Redis cache-aside |
| `orders-v0.1.0` | [e2e-v0.1.0.md](e2e-v0.1.0.md) | Scaffold: REST → Oracle → outbox → Kafka → consumers |

(`orders-v0.2.0` product/Redis behavior is covered in the v0.3.0 guide,
section 4 — it was re-verified there as part of the full stack.)

## Conventions

- **Pre-conditions**: state the environment must be in before starting.
- **Steps**: copy-pasteable `curl` commands with the expected result of each.
- **Post-conditions**: observable state that proves the milestone works.
- Run everything from the repo root unless stated otherwise.
