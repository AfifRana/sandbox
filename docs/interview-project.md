# Interview Project Brief: Weather Watch

## Purpose

Build a small, disposable project to discuss senior-level engineering decisions in an interview. The project should be understandable in a short walkthrough while demonstrating a realistic path through a REST API, CRUD, persistence, caching, asynchronous messaging, scheduled work, batch processing, and an external API.

This is an interview showcase, not a production commitment. It is not intended to be merged into `main` and may be deleted after the interview. It lives in a separate disposable Git worktree on the `feature/spring-basic` branch, so it can be removed without touching `main`.

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
- Persist forecast snapshots uniquely by location and forecast time, and update an existing snapshot on repeated runs.
- Retry transient Open-Meteo provider errors up to a bounded attempt count; fail a partition on other errors rather than silently dropping forecast data.
- Record job and step outcomes in Spring Batch metadata so failed runs can be diagnosed and restarted with the same job parameters.
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
5. A configurable scheduler launches a Spring Batch partitioned job (default: once per hour). A manager creates deterministic location-ID range partitions and dispatches worker steps with a configured concurrency limit. Each worker reads its assigned locations in chunks, fetches forecasts from Open-Meteo, writes forecast snapshots to PostgreSQL, and warms Redis.
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

The scheduled batch uses a one-hour fixed delay by default, a partition grid size of four, at most four worker threads, a chunk size of ten, and up to three attempts for transient provider exceptions. Override these with `WEATHER_BATCH_FIXED_DELAY`, `WEATHER_BATCH_GRID_SIZE`, `WEATHER_BATCH_MAX_WORKERS`, `WEATHER_BATCH_CHUNK_SIZE`, and `WEATHER_BATCH_RETRY_LIMIT`. Set `WEATHER_BATCH_ENABLED=false` to disable automatic runs.

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
- [x] Build and verify the app, PostgreSQL, Redis, Kafka, and deterministic Open-Meteo stub together with Docker Compose; the isolated runner cleans its own containers, network, and volumes.
- [x] Implement location CRUD and PostgreSQL persistence with Flyway schema migration, request validation, and H2-backed HTTP tests.
- [x] Implement the Open-Meteo current-forecast adapter, forecast endpoint, and Redis cache-aside behavior with a configurable TTL.
- [x] Implement keyed Kafka location events for location create/update/delete and idempotent forecast cache invalidation.
- [x] Implement the scheduled, partitioned Spring Batch forecast-refresh job, persistence, retry policy, and cache warming.
- [x] Add and run unit, batch integration, and container-backed end-to-end tests.
- [x] Verify the complete demo flow and document exact run commands.

## End-to-end test plan

### Test approach

Run the application, PostgreSQL, Redis, Kafka, and a WireMock Open-Meteo stub in a repeatable, isolated Docker Compose E2E environment. The PowerShell runner uses an isolated project name, unique host ports, and deterministic provider responses; it runs 15 scenarios covering CRUD, validation, cache reuse and TTL expiry, Kafka invalidation and keyed events, provider failure and timeout, partitioned and scheduled batch runs, transient retry, permanent failure, and a Kafka outage, and prints a pass/fail table. It captures service logs on failure and removes only its own containers, network, and volumes in a `finally` block. The Maven unit and batch integration tests exercise validation, retries, partitioning, repeated jobs, and provider behavior. Keep any manual smoke check against the public API optional.

### E2E run and cleanup procedure

The current local stack is defined in `compose.yaml`. Start it with `docker compose up --build -d`, check health with `docker compose ps`, and call `http://localhost:8080/actuator/health/readiness`. View logs with `docker compose logs -f app`. `docker compose down` stops the normal demo stack but preserves its named volumes.

1. Run `.\scripts\e2e.ps1` from PowerShell. It starts the app, PostgreSQL, Redis, Kafka, and WireMock with a unique Compose project name and unused host ports.
2. The E2E runner waits for app/stub readiness, runs each scenario with uniquely named locations, injects provider failures through WireMock admin mappings, stops and restarts only its own Kafka container for the outage scenario, and fails the run if any scenario fails.
3. The runner captures service logs before cleanup if an assertion fails. A `finally` block always removes only the isolated test project and its dedicated volumes. Batch metadata, test records, Redis data, and Kafka state are discarded with that run's volumes.
4. If cleanup itself fails, use the printed project name to remove only that project:

   ```powershell
   docker compose --project-name <project-name> --profile e2e down --volumes --remove-orphans
   ```

   Do not use this cleanup command with the normal demo Compose project or any project containing data you want to retain. For the regular demo environment, `docker compose down` preserves named volumes. Its destructive full reset is `docker compose down --volumes --remove-orphans`; only use it when local data may be discarded.

### Acceptance scenarios

Every scenario below is verified automatically. Container E2E (`scripts/e2e.ps1`) covers all except the three marked **Maven only**, which cannot be observed from outside the app: bounded concurrency, restart of a failed job (the app exposes no restart endpoint), and literal duplicate delivery of one event (the consumer's eviction is idempotent and unit-tested; E2E instead sends repeated update events). Maven tests also cover most other rows at unit/integration level.

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
| Consume duplicate location event | Deliver a duplicate location-change event | **Maven only for literal duplicates:** cache eviction remains safe and location cache entry stays absent |
| Run partitioned forecast batch | Insert locations spanning multiple partitions and launch the job | Every location is assigned to one partition, processed, persisted, and cached |
| Bound partition concurrency | Configure a worker limit and observe active workers | **Maven only:** concurrent workers never exceed the configured limit |
| Schedule batch launch | Enable the scheduler with a short test interval | A job execution is launched without starting overlapping duplicate executions |
| Retry a transient provider error | Make a provider call fail transiently, then succeed | Configured retry behavior succeeds without duplicate or corrupt forecast snapshots |
| Handle a permanent item failure | Return a non-retryable error for one location | The affected partition/job fails visibly; no forecast is silently skipped |
| Restart a failed job | Rerun a failed job instance with the same parameters after fixing the cause | **Maven only:** Spring Batch accepts the same parameters and the job completes, with no duplicate snapshots |
| Repeat a completed job | Rerun with new job parameters | Snapshot uniqueness updates existing location/time rows rather than duplicating them |
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

Run `mvn verify` for the unit and Spring Batch integration tests. MockMvc tests use an in-memory H2 database; the external HTTP client uses deterministic mocks, and Redis cache operations are unit-tested with mocked Redis operations. Coverage includes location CRUD, validation, cache hit/miss and TTL, Open-Meteo response mapping and failures, partition range assignment, scheduled job processing, idempotent snapshots, and transient retries. Run `.\scripts\e2e.ps1` for the container-backed E2E flow; it uses WireMock and isolated Compose resources, so it does not depend on public API availability.
