# Interview Project Brief: Weather Watch

## Purpose

Build a small, disposable project to discuss senior-level engineering decisions in an interview. The project should be understandable in a short walkthrough while demonstrating a realistic path through a REST API, CRUD, persistence, caching, asynchronous messaging, scheduled work, batch processing, and an external API.

This is an interview showcase, not a production commitment. It is not intended to be merged into `main` and may be deleted after the interview. The current workspace is inside an existing Git repository; writing this brief does not create a separate repository, branch, or worktree. If strict source-control isolation is required, implement it in a separate disposable working copy.

## Proposed product

**Weather Watch** lets a user maintain a small collection of saved locations and request weather forecasts for them.

### MVP capabilities

- REST CRUD for saved locations, each with a name, latitude, and longitude.
- Persist location records in PostgreSQL.
- Retrieve current conditions from [Open-Meteo](https://open-meteo.com/) using a saved location's coordinates.
- Cache successful forecast responses in Redis for 10 minutes under a location-specific key. Location-change events evict that key; until the event is consumed, a coordinate update may briefly leave the prior forecast cached.
- Publish location-created, location-updated, and location-deleted events to Kafka.
- Consume location-change events and invalidate the affected forecast cache entry.
- Use the location ID as the Kafka key and include a unique event ID, operation type, and event timestamp.
- Use Spring's scheduler to launch a configurable forecast-refresh Spring Batch job.
- Partition the batch workload by a deterministic range of location IDs; have each worker process its assigned locations, fetch forecasts from Open-Meteo, persist forecast snapshots, and warm the Redis forecast cache.
- Bound partition concurrency so worker load respects database capacity and Open-Meteo rate limits; make writes idempotent so retrying a partition does not create duplicate or inconsistent snapshots.
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
3. Forecast requests check Redis first. On a miss, an HTTP adapter calls Open-Meteo for current conditions, then caches the successful response for the configured TTL.
4. Location changes publish events to Kafka. A consumer handles events idempotently and invalidates the relevant forecast cache entry.
5. A configurable scheduler launches a Spring Batch partitioned job. A manager creates deterministic location-ID partitions and dispatches worker steps with a configured concurrency limit. Each worker reads its assigned locations in chunks, fetches forecasts from Open-Meteo, writes forecast snapshots to PostgreSQL, and warms Redis.
6. The API remains usable when the event consumer or batch job is temporarily unavailable; event delivery and batch outcomes should be observable and tested.

### Deliberate scope limits

- No user accounts, authentication, frontend, deployment, or production SLOs.
- No multi-provider routing or generalized workflow platform.
- Keep configuration and local startup instructions small and explicit.
- Add only the operational visibility needed to explain the request and event flows.

## Containerized development and demo

The runnable application and its local dependencies must be containerized so a reviewer can start the complete demo consistently without installing Java, PostgreSQL, Redis, or Kafka on the host.

- Provide a production-minded, multi-stage `Dockerfile` for the Spring Boot application and a `.dockerignore` that excludes build output, Git metadata, local secrets, and unrelated files.
- Provide a Docker Compose definition for the application, PostgreSQL, Redis, and Kafka. Configure service DNS names for app connections, persistent named volumes for normal local runs, health checks, and startup dependencies based on readiness rather than container start order alone.
- Keep configuration in documented environment variables and a safe local example file. Never commit credentials; avoid publishing database, Redis, or Kafka ports to the host unless the demo or tests require them.
- Support a repeatable Compose-based end-to-end test environment. Use an isolated Compose project name and dedicated volumes for E2E so cleanup cannot remove unrelated local data.
- Include the exact build, start, health/status, test, log, and cleanup commands after the actual Compose and build files are established.

## Senior-level discussion points

- Why PostgreSQL owns durable location data while Redis stores disposable forecast data.
- Cache-aside behavior, TTL selection, invalidation, and stale-data tradeoffs.
- Kafka delivery semantics, duplicate events, idempotent consumers, and eventual consistency.
- Tradeoffs of waiting for Kafka acknowledgement inside a CRUD request versus a transactional outbox, including the database-commit-after-publish failure window.
- Why scheduled batch refresh is appropriate for refreshing many saved locations, and how partition sizing, bounded concurrency, chunk size, retries, skip policy, job parameters, and restartability affect behavior.
- How partition boundaries avoid missed or duplicated locations, how worker ExecutionContext carries each partition's input, and how failed partitions can be restarted safely.
- How to prevent overlapping scheduled launches and distinguish scheduler triggers from Spring Batch job execution and metadata.
- External API timeouts, error mapping, validation, and test isolation.
- Where transaction boundaries end and how to handle persistence plus event publication reliably. Start with the simplest viable approach; document the tradeoff and avoid implying atomicity if it is not implemented.
- How the design could evolve without building speculative abstractions in the MVP.

## Progress

Update this checklist as work is completed. Do not mark implementation or test work complete until it has been run and verified.

- [x] Define the project goal, disposable lifecycle, and initial scope.
- [x] Document a proposed component architecture and showcase flow.
- [x] Define an initial E2E test plan.
- [x] Scaffold the Spring Boot application with Java 21, Maven, Spring Boot starters, configuration, and a context smoke test.
- [ ] Build and verify the app, PostgreSQL, Redis, and Kafka containers together with Docker Compose. Compose syntax is validated; container startup still needs a running Docker daemon.
- [x] Implement location CRUD and PostgreSQL persistence with Flyway schema migration, request validation, and H2-backed HTTP tests.
- [x] Implement the Open-Meteo current-forecast adapter, forecast endpoint, and Redis cache-aside behavior with a configurable TTL.
- [x] Implement keyed Kafka location events for location create/update/delete and idempotent forecast cache invalidation.
- [ ] Implement the scheduled, partitioned Spring Batch forecast-refresh job, persistence, and cache warming.
- [ ] Add and run unit, integration, and end-to-end tests.
- [ ] Verify the complete demo flow and document exact run commands.

## End-to-end test plan

### Test approach

Run the application, PostgreSQL, Redis, and Kafka as containers in a repeatable, isolated Docker Compose E2E environment. Stub or intercept Open-Meteo HTTP responses for deterministic tests; keep at least one optional manual smoke check against the public API. Tests should not depend on public network availability. Give test records and events a unique run identifier and clean them up even when an assertion fails.

### E2E run and cleanup procedure

The current local stack is defined in `compose.yaml`. Start it with `docker compose up --build -d`, check health with `docker compose ps`, and call `http://localhost:8080/actuator/health/readiness`. View logs with `docker compose logs -f app`. `docker compose down` stops the normal demo stack but preserves its named volumes.

1. Start an isolated Compose project for E2E with its own project name and dedicated volumes; wait for health checks for the app and required dependencies.
   Use `docker compose -p weather-watch-e2e up --build -d` after stopping any normal stack that is using the same host API port.
2. Run the E2E suite against that stack and use a unique run identifier for created locations and related records/events.
3. In the test suite's teardown/finally path, delete test-created locations and forecast snapshots, clear the associated Redis keys, and verify the test Kafka consumer group or isolated test topic does not leak into later runs. Preserve Spring Batch execution metadata during test assertions; it may be discarded with the dedicated E2E database afterward.
4. Collect failure diagnostics (test summary and relevant container logs) before tearing down the isolated stack.
5. Stop and remove only the E2E Compose project and its dedicated volumes:

   ```sh
   docker compose -p weather-watch-e2e down --volumes --remove-orphans
   ```

   This removes named volumes and therefore deletes E2E database, cache, and broker state. Do not use this cleanup command with the normal demo Compose project or any project containing data you want to retain.

6. For the regular demo environment, `docker compose down` preserves named volumes. Its destructive full reset is `docker compose down --volumes --remove-orphans`; only use it when local data may be discarded.

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
| Publish location events | Create, update, and delete locations | Each operation publishes a keyed event with an event ID and operation type |
| Broker publish failure | Stop Kafka and attempt to create a location | API returns service unavailable and the location write rolls back |
| Consume duplicate location event | Deliver a duplicate location-change event | Cache eviction remains safe and location cache entry stays absent |
| Run partitioned forecast batch | Insert locations spanning multiple partitions and launch the job | Every location is assigned to one partition, processed, persisted, and cached |
| Bound partition concurrency | Configure a worker limit and observe active workers | Concurrent workers never exceed the configured limit |
| Schedule batch launch | Enable the scheduler with a short test interval | A job execution is launched without starting overlapping duplicate executions |
| Retry a transient provider error | Make a provider call fail transiently, then succeed | Configured retry behavior succeeds without duplicate or corrupt forecast snapshots |
| Handle a permanent item failure | Return a non-retryable error for one location | Failure is visible in job/step results; other locations follow the documented skip/fail policy |
| Restart a failed partition | Fail one partition, then restart the job with supported parameters | Completed work is not duplicated and the failed partition can complete safely |
| Restart or repeat a job | Rerun with supported job parameters after a partial or completed execution | Batch metadata and idempotent writes prevent duplicate or inconsistent forecast state |
| Handle provider failure | Make the provider return an error or time out | API returns an explicit service error and does not cache a fake success |
| Handle duplicate events | Deliver the same location-change event more than once | Consumer remains safe and the cache reaches the same final state |

### Evidence to capture for the demo

- Docker Compose build/start health status and the command used to launch the isolated E2E stack.
- Successful CRUD requests and responses.
- A provider-call count or test assertion showing forecast cache reuse.
- Kafka event publication and consumer processing for a location change.
- Verify location event publish failures are visible and do not leave a successful-looking CRUD response.
- Spring Batch job/manager/worker execution summary, partition assignments, processed item counts, and persisted forecast snapshot evidence.
- E2E test command, summary, cleanup result, and any known limitations.

Run `mvn test` for the saved-location and forecast API tests. The MockMvc tests use an in-memory H2 database and clear location records before each test; the external client uses a deterministic mock HTTP server, and the Redis cache adapter is unit-tested with mocked Redis operations. Coverage includes location CRUD, validation, missing IDs, cache hit/miss, cache TTL serialization, Open-Meteo response mapping, and provider failure handling. These tests do not require Docker and are not yet the planned container-backed E2E suite; E2E test implementation and automated cleanup wiring remain future work.
