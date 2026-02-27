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

![Module diagram](https://github.com/user-attachments/assets/0a541237-4e1f-42a0-bd9d-487089dad69a)

The application is organized into five modules:

- **Api** - UI controllers and scheduled jobs
- **Conjunction** - Detection algorithms and conjunction storage
- **Ingestion** - Orchestrates catalog synchronization from Space-Track
- **Satellite** - Satellite entity and repository
- **Spacetrack** - HTTP client for Space-Track.org API

## Coarse and Fine Scanning

The detection algorithm uses a two-step approach:

1. **Coarse sweep**: Pre-computes satellite positions using SGP4 with Hermite interpolation, then uses spatial grid
   indexing to efficiently find nearby satellites within tolerance
2. **Refinement**: Solves quadratic equation to find precise TCA and miss distance, filtering by 5 km collision
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
docker compose up
```

This starts PostgreSQL and the application. The app will be available at `http://localhost:8080`.

### 2B. Running Locally for Development

```bash
docker compose up postgres -d
./mvnw spring-boot:run
```
