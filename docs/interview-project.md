# Interview Project Brief: Weather Watch

## Purpose

Build a small, disposable project to discuss senior-level engineering decisions in an interview. The project should be understandable in a short walkthrough while demonstrating a realistic path through a REST API, CRUD, persistence, caching, asynchronous messaging, scheduled work, batch processing, and an external API.

This is an interview showcase, not a production commitment. It is not intended to be merged into `main` and may be deleted after the interview. The current workspace is inside an existing Git repository; writing this brief does not create a separate repository, branch, or worktree. If strict source-control isolation is required, implement it in a separate disposable working copy.

## Proposed product

**Weather Watch** lets a user maintain a small collection of saved locations and request weather forecasts for them.

### MVP capabilities

- REST CRUD for saved locations, each with a name, latitude, and longitude.
- Persist location records in PostgreSQL.
- Retrieve a forecast from [Open-Meteo](https://open-meteo.com/) using the saved coordinates.
- Cache successful forecast responses in Redis with a documented TTL.
- Publish location-created, location-updated, and location-deleted events to Kafka.
- Consume location-change events and invalidate the affected forecast cache entry.
- Use Spring's scheduler to launch a configurable forecast-refresh Spring Batch job.
- Have the batch job read saved locations, fetch forecasts from Open-Meteo, persist forecast snapshots, and warm the Redis forecast cache.
- Record job and step outcomes so failed runs can be diagnosed and safely retried.
- Provide clear validation errors and handle external-service failures without returning misleading successful responses.

The Open-Meteo forecast API does not require an API key for its public non-commercial API. Keep the provider behind a small application interface so its HTTP details do not leak into API or domain code.

### Proposed API shape

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/locations` | Create a saved location |
| `GET` | `/api/locations` | List saved locations |
| `GET` | `/api/locations/{id}` | Get one saved location |
| `PUT` | `/api/locations/{id}` | Update a saved location |
| `DELETE` | `/api/locations/{id}` | Delete a saved location |
| `GET` | `/api/locations/{id}/forecast` | Get a forecast, using Redis when available |

This API is a starting proposal; exact request fields, forecast horizon, and response schema will be settled during implementation.

## Proposed architecture

1. The REST layer validates requests and delegates to application services.
2. Application services use a repository to persist saved locations in PostgreSQL.
3. Forecast requests check Redis first. On a miss, they use the latest persisted forecast when suitable or call Open-Meteo, then cache the successful response for the configured TTL.
4. Location changes publish events to Kafka. A consumer handles events idempotently and invalidates the relevant forecast cache entry.
5. A configurable scheduler launches a Spring Batch job that reads locations in chunks, fetches their forecasts from Open-Meteo, writes forecast snapshots to PostgreSQL, and warms Redis.
6. The API remains usable when the event consumer or batch job is temporarily unavailable; event delivery and batch outcomes should be observable and tested.

### Deliberate scope limits

- No user accounts, authentication, frontend, deployment, or production SLOs.
- No multi-provider routing or generalized workflow platform.
- Keep configuration and local startup instructions small and explicit.
- Add only the operational visibility needed to explain the request and event flows.

## Senior-level discussion points

- Why PostgreSQL owns durable location data while Redis stores disposable forecast data.
- Cache-aside behavior, TTL selection, invalidation, and stale-data tradeoffs.
- Kafka delivery semantics, duplicate events, idempotent consumers, and eventual consistency.
- Why scheduled batch refresh is appropriate for refreshing many saved locations, and how chunk size, retries, skip policy, job parameters, and restartability affect behavior.
- How to prevent overlapping scheduled launches and distinguish scheduler triggers from Spring Batch job execution and metadata.
- External API timeouts, error mapping, validation, and test isolation.
- Where transaction boundaries end and how to handle persistence plus event publication reliably. Start with the simplest viable approach; document the tradeoff and avoid implying atomicity if it is not implemented.
- How the design could evolve without building speculative abstractions in the MVP.

## Progress

Update this checklist as work is completed. Do not mark implementation or test work complete until it has been run and verified.

- [x] Define the project goal, disposable lifecycle, and initial scope.
- [x] Document a proposed component architecture and showcase flow.
- [x] Define an initial E2E test plan.
- [ ] Scaffold the Spring Boot application and local PostgreSQL, Redis, and Kafka setup.
- [ ] Implement location CRUD and persistence.
- [ ] Implement the Open-Meteo adapter and Redis forecast cache.
- [ ] Implement Kafka location events and cache invalidation.
- [ ] Implement the scheduled Spring Batch forecast-refresh job, persistence, and cache warming.
- [ ] Add and run unit, integration, and end-to-end tests.
- [ ] Verify the complete demo flow and document exact run commands.

## End-to-end test plan

### Test approach

Use the real application with PostgreSQL, Redis, and Kafka in a repeatable local test environment. Stub or intercept Open-Meteo HTTP responses for deterministic tests; keep at least one optional manual smoke check against the public API. Tests should not depend on public network availability or leave records behind.

### Acceptance scenarios

| Scenario | Exercise | Expected result |
|---|---|---|
| Create and read a location | `POST`, then `GET` the returned ID | Valid location is returned and persisted |
| List locations | Create multiple locations, then `GET` collection | The created records appear in the response |
| Update a location | `PUT`, then read it again | Persisted values reflect the update |
| Delete a location | `DELETE`, then fetch its ID | Delete succeeds and subsequent lookup returns not found |
| Reject invalid coordinates | Submit out-of-range latitude or longitude | Client receives a validation error; no invalid record is stored |
| Fetch a forecast on a cache miss | Request a location forecast with an empty cache | Open-Meteo is called and a successful response is returned and cached |
| Reuse a cached forecast | Repeat the same request before TTL expiry | Cached response is returned and provider is not called again |
| Expire forecast data | Advance or wait past the configured TTL | Next request calls the provider and refreshes the cache |
| Invalidate on location change | Update a location and wait for its Kafka event to be consumed | The old forecast cache entry is removed; the next request refreshes it |
| Run forecast batch | Insert several locations and launch the batch job | Each valid location is read, forecast data is fetched, a snapshot is persisted, and Redis is warmed |
| Schedule batch launch | Enable the scheduler with a short test interval | A job execution is launched without starting overlapping duplicate executions |
| Retry a transient provider error | Make a provider call fail transiently, then succeed | Configured retry behavior succeeds without duplicate or corrupt forecast snapshots |
| Handle a permanent item failure | Return a non-retryable error for one location | Failure is visible in job/step results; other locations follow the documented skip/fail policy |
| Restart or repeat a job | Rerun with supported job parameters after a partial or completed execution | Batch metadata and idempotent writes prevent duplicate or inconsistent forecast state |
| Handle provider failure | Make the provider return an error or time out | API returns an explicit service error and does not cache a fake success |
| Handle duplicate events | Deliver the same location-change event more than once | Consumer remains safe and the cache reaches the same final state |

### Evidence to capture for the demo

- Successful CRUD requests and responses.
- A provider-call count or test assertion showing forecast cache reuse.
- Kafka event publication and consumer processing for a location change.
- Spring Batch job/step execution summary, processed item count, and persisted forecast snapshot evidence.
- E2E test command, summary, and any known limitations.

Commands and the exact test framework are intentionally TBD until the application scaffold exists.
