# Weather Watch

A small, disposable, containerized Spring Boot project proposal for a senior-engineering interview. It will demonstrate a REST API with CRUD, PostgreSQL persistence, Redis caching, Kafka events, scheduled partitioned Spring Batch forecast ingestion, and integration with the public [Open-Meteo API](https://open-meteo.com/).

> **Status:** Planning only. This repository currently has no application source code. The design below describes the intended project, not features that are already implemented.
>
> **Project lifecycle:** This is an isolated interview showcase, not a production feature. It is not intended to be merged into `main` and may be deleted after the interview. These documents do not create Git isolation: the current workspace is inside an existing repository. Keep implementation in a separate disposable working copy if strict repository isolation is required.

## What we'll build

Users will manage saved weather locations through a small REST API. The application will store locations and forecast snapshots in PostgreSQL, retrieve forecasts from Open-Meteo, cache forecasts in Redis, and publish location-change events to Kafka. A scheduler will launch a partitioned Spring Batch job to refresh forecasts for saved locations on a configurable interval, processing location partitions with bounded concurrency. Docker Compose will run the application and its local infrastructure as containers for a repeatable demo and E2E test environment.

### Planned architecture

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
```mermaid
flowchart TD
    subgraph Client["Client Layer"]
        Demo["curl or API client"]
    end
    subgraph App["Application Layer - Spring Boot"]
        Api["REST API in app container"]
        Service["Location and weather services"]
        Producer["Kafka event publisher"]
        Consumer["Kafka event consumer"]
        Scheduler["Forecast refresh scheduler"]
        Manager["Batch partition manager"]
        Worker["Partition worker steps"]
    end
    subgraph Data["Data Layer"]
        Pg[("Locations and forecasts")]
        Cache[("Redis forecast cache")]
        Kafka[("Kafka")]
    end
    subgraph External["External Service"]
        Meteo["Open-Meteo API"]
    end

    Demo -->|"HTTP requests"| Api
    Api -->|"CRUD and forecast requests"| Service
    Service -->|"persist locations"| Pg
    Service -->|"read or write forecasts"| Cache
    Service -->|"cache miss"| Meteo
    Service -->|"publish location changes"| Producer
    Producer -->|"location events"| Kafka
    Kafka -->|"deliver events"| Consumer
    Consumer -->|"invalidate stale forecast"| Cache
    Scheduler -->|"launch refresh job"| Manager
    Manager -->|"dispatch bounded partitions"| Worker
    Worker -->|"read and write forecasts"| Pg
    Worker -->|"fetch forecasts"| Meteo
    Worker -->|"warm forecast cache"| Cache
```

### Run and showcase

The intended local workflow is Docker Compose: build and start the application, PostgreSQL, Redis, and Kafka together; then exercise the API and scheduled batch flow. Exact Compose file names, environment variables, health checks, and commands will be added with the application scaffold.

1. Build and start the application and its PostgreSQL, Redis, and Kafka dependencies with Docker Compose.
2. Create, list, update, and delete a saved location using the REST API.
3. Request a forecast twice and show that the second request is served from Redis.
4. Change a location and show the Kafka event being consumed and its cached forecast invalidated.
5. Trigger or wait for the scheduled Spring Batch job; show it dividing locations into partitions, processing those partitions with bounded concurrency, and persisting the results.
6. Run the end-to-end tests to demonstrate the API, persistence, cache, Kafka event flow, partitioned batch processing, and Open-Meteo integration together.

See [the project brief](docs/interview-project.md) for containerization requirements, E2E cleanup steps, scope, progress, and the test plan.

## Project documents

- [Project brief, scope, progress, and E2E test plan](docs/interview-project.md)
- [Proposed architecture and component relationships](.github/modernize/assessment/engines/facts/architecture-diagram.md)
