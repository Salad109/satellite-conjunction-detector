# Conjunction Tolerance Tuning

Final tuning of the coarse sweep tolerance parameter, with prepass, step ratio, and interpolation already optimized.

## Parameters

- **prepass-tolerance-km**: Fixed at 10.0 km (from prepass tuning)
- **step-second-ratio**: Fixed at 10 (from step ratio tuning)
- **interpolation-stride**: Fixed at 6 (from interpolation tuning)
- **tolerance-km**: Coarse detection threshold (swept 50-1000 km)
- **lookahead-hours**: Fixed at 24 hours
- **threshold-km**: Final conjunction threshold (fixed at 5.0 km)

## Analysis

Tolerance controls the coarse sweep step size (step = tolerance / 10). Three stages dominate:

- **Propagate** scales as 1/x - fewer steps at larger tolerance
- **Check Pairs** scales as a/x + bx - fewer steps but more spatial grid cells per step
- **Grouping** scales as x^2 - quadratically more detections to cluster

The rest (pair reduction, filter, propagator build, refine) are roughly constant.

| Tolerance | Prop+Check | Grouping | Total  |
|-----------|------------|----------|--------|
| 90 km     | 58.35s     | 2.67s    | 63.91s |
| 250 km    | 28.18s     | 8.62s    | 40.66s |
| 490 km    | 25.19s     | 21.78s   | 51.74s |

## Conclusion

**Optimal tolerance is 250 km (step size 25s).**

![Total Processing Time](6-conjunction-tolerance/1_total_time.png)

![Time Breakdown](6-conjunction-tolerance/2_time_breakdown.png)

![Time Breakdown Stacked](6-conjunction-tolerance/3_time_breakdown_stacked.png)

![Conjunctions Detected](6-conjunction-tolerance/4_conjunctions.png)

## Running the Benchmark

```bash
# Run on Linux
./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-conjunction

# Run on Windows
./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-conjunction"

# *you must have a running PostgreSQL instance with the satellite catalog loaded
```
