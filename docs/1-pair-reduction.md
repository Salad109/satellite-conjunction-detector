# Pair Reduction Filter Order

With ~30,000 tracked objects, naive N^2 comparison requires checking 441 million satellite pairs. Three geometric
filters reduce this to 5.1% of original pairs (~22.5M). This experiment determines the optimal filter ordering at
12.5 km tolerance.

## Filters

Three filters eliminate pairs that cannot possibly collide:

| Filter       | Description                               |
|--------------|-------------------------------------------|
| **Altitude** | Require overlapping perigee/apogee ranges |
| **Debris**   | Skip pairs where both objects are debris  |
| **Plane**    | Check orbital plane intersection geometry |

First filter runs on all pairs. Subsequent filters only run on pairs that passed previous filters.

## Filter Order Analysis

All orderings produce identical final pair counts (22.5M pairs, 5.1% of original). Only execution time differs.

| Order | Time  | Relative |
|-------|-------|----------|
| ADP   | 16.3s | baseline |
| DAP   | 16.5s | +1%      |
| APD   | 20.2s | +24%     |
| DPA   | 68.6s | +320%    |
| PAD   | 76.4s | +368%    |
| PDA   | 77.0s | +372%    |

**Optimal order: Altitude, Debris, Plane (ADP)** - nearly tied with DAP.

## Running the Benchmark

```bash
# Linux
./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-filter

# Windows
./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-filter"
# *you must have a running PostgreSQL instance with the satellite catalog loaded 
```

![Total Time by Filter Order](1-pair-reduction/1_total_time.png)

![Time Comparison](1-pair-reduction/2_time_comparison.png)
