# Conjunction Detector

All-vs-all satellite conjunction screener. Scans the full public catalog (~30,000 objects) for sub-5 km close approaches
in under 30 seconds on consumer hardware.

Validated against [CelesTrak SOCRATES](https://celestrak.org/SOCRATES/): when filtered to equivalent scope
(payload-vs-catalog, excluding intra-constellation pairs), detection counts match within 8%. Full all-vs-all screening
finds ~48,000 conjunctions per 24h window, including debris-vs-debris pairs that SOCRATES excludes.

Backtested against
the 2009 Iridium 33 / Cosmos 2251 collision - the pipeline flags the event with 10 ms TCA accuracy.

|                  | This                                        | SOCRATES (CelesTrak)     |
|------------------|---------------------------------------------|--------------------------|
| Input            | Space-Track TLEs                            | Space-Track TLEs         |
| Propagator       | SGP4 (Orekit)                               | SGP4 (STK)               |
| Window           | 24h (configurable)                          | 7 days                   |
| Threshold        | 5 km (configurable)                         | 5 km                     |
| Scope            | All-vs-all (~450M pairs)                    | Primaries vs secondaries |
| 24h conjunctions | ~48,000 (19,374 filtered to SOCRATES scope) | 17,893 (in same window)  |
| Compute time     | ~30 seconds (~2,880x realtime)              | ~10 hours (17x realtime) |

## How It Works

The detection pipeline has four stages:

### 1. Propagation (SGP4 + Hermite interpolation)

Rather than calling SGP4 at every timestep, the propagator stage evaluates SGP4 at knot points spaced minutes apart and
fills intermediate positions using cubic Hermite interpolation on position and velocity. This cuts expensive SGP4 calls
by 50x with negligible accuracy loss.

### 2. Coarse sweep (spatial grid indexing)

At each timestep, all satellite positions are hashed into a 1024 x 1024 x 1024 3D cell grid. Candidate pairs are
generated only from same and neighbor cells. This eliminates the O(n^2) pairwise comparison.

### 3. Grouping

Coarse detections are sorted by pair and timestep, clustered into events, and reduced to the closest detection per
event.

### 4. Refinement

Between two interpolated timesteps (~9 seconds apart), relative motion is effectively linear, so squared distance is
quadratic, therefore the minimum of a quadratic is just one division. No golden section, no Brent's method, no iterative
SGP4 calls. Most candidates get discarded here because the analytical minimum exceeds the 5 km threshold. Only survivors
get a single SGP4 call to confirm. Events that pass are scored with collision probability (Orekit's Laas2015 method,
covariance synthesized from empirical SGP4 error models).

## Parameter Tuning

The [/docs](docs) directory contains experiments from benchmarking each tunable parameter. Individually safe choices
compound in complex ways when combined, so the Pareto analysis sweeps all parameters simultaneously.

| # | Experiment                                            | What it sweeps                                     |
|---|-------------------------------------------------------|----------------------------------------------------|
| 1 | [Step Ratio](docs/1-step-ratio)                       | Time step size                                     |
| 2 | [Interpolation Stride](docs/2-interpolation-stride)   | SGP4 calls per time step via interpolation spacing |
| 3 | [Cell Size Ratio](docs/3-cell-size-ratio)             | Spatial grid cell size                             |
| 4 | [Conjunction Tolerance](docs/4-conjunction-tolerance) | Coarse scan distance threshold in km               |
| 5 | [Pareto Frontier](docs/5-pareto-frontier)             | All parameters simultaneously                      |
| 6 | [Garbage Collector](docs/6-gc)                        | GC impact on pipeline throughput                   |
| 7 | [Subwindow Count](docs/7-subwindow-count)             | Memory partitioning for peak heap reduction        |

Selected Pareto-optimal configurations:

| Step ratio | Stride | Cell ratio | Cell (km) | Accuracy   | Time    |
|------------|--------|------------|-----------|------------|---------|
| 9          | 25     | 1.0        | 72.0      | 100.00%    | 30s     |
| 8          | 25     | 1.0        | 72.0      | 99.99%     | 27s     |
| 8          | 40     | 1.3        | 55.4      | 99.89%     | 23s     |
| **8**      | **50** | **1.5**    | **48.0**  | **99.43%** | **21s** |
| 7          | 50     | 1.2        | 60.0      | 98.42%     | 20s     |

Default configuration (bold) is the default a good compromise, trading 0.6% accuracy for a 1.4x speedup over the safest
option.

## Architecture

![Module diagram](https://github.com/user-attachments/assets/0a541237-4e1f-42a0-bd9d-487089dad69a)

Five Spring Modulith modules:

- **Ui** - Controllers and scheduled jobs
- **Conjunction** - Detection algorithms and conjunction storage
- **Ingestion** - Catalog synchronization from Space-Track
- **Satellite** - Satellite entity and repository
- **Spacetrack** - HTTP client for Space-Track.org API

## Tech Stack

- Java 25
- Spring Boot 4 / Spring Modulith
- Orekit 13
- PostgreSQL / Flyway
- HTMX / Thymeleaf

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

### 2. Run

```bash 
# Both PostgreSQL and the application
docker compose up
# Local
docker compose up postgres -d
./mvnw spring-boot:run
```
