# Conjunction Detector

Satellite conjunction detection system that monitors orbital objects for potential collisions. Ingests data from
[Space-Track.org](https://www.space-track.org), propagates orbits using Orekit, and identifies close approaches
between satellites.

## Tech Stack

- Java 25
- Spring Boot 4 / Spring Modulith
- Orekit 13
- PostgreSQL / Flyway
- HTMX / Thymeleaf

## Architecture

![Module diagram](https://github.com/user-attachments/assets/320db956-f3b7-458e-80f7-eb8e6cf3fa64)

The application is organized into five modules:

- **Api** - UI controllers and scheduled jobs
- **Conjunction** - Detection algorithms and conjunction storage
- **Ingestion** - Orchestrates catalog synchronization from Space-Track
- **Satellite** - Satellite entity, repository, and pair reduction logic
- **Spacetrack** - HTTP client for Space-Track.org API

## Conjunction Candidate Reduction

With ~30,000 tracked objects, the naive approach would check over 400 million satellite pairs. The system applies
sequential geometric filters to reduce this to ~3.4% (14.7M pairs).
See [docs/1-pair-reduction.md](docs/1-pair-reduction.md) for details.

## Coarse and Fine Scanning

The detection algorithm uses a two-step approach:

1. **Coarse sweep**: Pre-computes satellite positions using SGP4 with linear interpolation, then uses spatial grid
   indexing to efficiently find nearby satellites within tolerance
2. **Refinement**: Brent's method optimization to find precise TCA and miss distance, filtering by 5 km collision
   threshold

See docs for tuning experiments and optimal parameters.

## Setup

### Prerequisites

- Java 25
- Docker Compose
- [Space-Track.org](https://www.space-track.org) account

### 1. Configure Environment

Copy the example environment file and fill in your Space-Track credentials:

```bash
cp .env.example .env
```

### 2A. Run with Docker Compose

```bash
docker-compose up
```

This starts PostgreSQL and the application. The app will be available at `http://localhost:8080`.

### 2B. Running Locally for Development

```bash
docker-compose up postgres
./mvnw spring-boot:run
```

### 3. Native Pair Reduction (Optional)

The pair reduction filter has a native C implementation using Panama FFM API that runs faster. If the native library is
not
available, the application automatically falls back to the Java implementation.

**Local Linux Development:**

```bash
cd src/main/c
make
```

**Docker:** The native library is compiled automatically.
