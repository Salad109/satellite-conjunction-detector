# Conjunction Tolerance Tuning

Final tuning of the coarse sweep tolerance parameter, with prepass, step ratio, and interpolation already optimized.

## Parameters

- **prepass-tolerance-km**: Fixed at 10.0 km (from prepass tuning)
- **step-second-ratio**: Fixed at 10 (from step ratio tuning)
- **interpolation-stride**: Fixed at 6 (from interpolation tuning)
- **tolerance-km**: Coarse detection threshold (swept from 50 to 400 km, incremented by 10)
- **lookahead-hours**: Fixed at 24 hours
- **threshold-km**: Final conjunction threshold (fixed at 5.0 km)

## Pipeline Stages

The conjunction detection pipeline consists of 7 stages:

1. **Pair Reduction**: Geometric filtering to reduce candidate pairs (~12s, constant)
2. **Filter**: Rebuilding catalog from reduced pairs (~0.4s, constant)
3. **Propagator Build**: Constructing TLE propagators (~0.08s, constant)
4. **Propagate**: Pre-computing positions for coarse sweep (decreases with tolerance)
5. **Check Pairs**: Distance checking during coarse sweep (decreases with tolerance)
6. **Grouping**: Clustering detections into events (increases with tolerance)
7. **Refine**: Brent's method optimization for precise TCA (increases with tolerance)

## Analysis

### Time Complexity Trade-off

The pipeline stages have opposing time complexities with respect to tolerance:

- **Propagate + Check Pairs - O(1/tolerance)**: Larger tolerance means larger step size, so fewer propagation calls and
  distance checks. Doubling tolerance roughly halves this time.
- **Grouping + Refine - O(tolerance)**: Larger tolerance catches more events that need grouping and refinement.
- **Pair Reduction, Filter, Propagator Build**: Essentially constant regardless of tolerance.

The optimal tolerance minimizes total time. As tolerance increases, propagate/check time decreases but grouping/refine
time increases. The U-shaped total time curve has its minimum where these opposing effects balance.

### Performance Curve

| Tolerance | Coarse (Prop+Check) | Refine (Group+Refine) | Total  |
|-----------|---------------------|-----------------------|--------|
| 100 km    | 29.01s              | 9.04s                 | 50.91s |
| 150 km    | 20.80s              | 14.25s                | 48.03s |
| 300 km    | 12.58s              | 30.43s                | 55.95s |

## Conclusion

**Optimal tolerance is 150 km with step size of 15 seconds**

This achieves lowest total time with balanced coarse and refine stages. Going lower wastes time on
excessive coarse iterations; going higher wastes time processing too many candidate events.

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

# *you must have a running PostgreSQL instance with the satellite catalog loaded, and orekit data files at src/main/resources/orekit-data
```
