# Pre-pass Tolerance Tuning

The pair reduction filter uses a geometric tolerance to determine which satellite pairs could potentially collide. A
larger tolerance includes more pairs (safer but slower), while a smaller tolerance excludes more pairs (faster but risks
missing conjunctions).

This experiment finds the minimum pre-pass tolerance that detects all conjunctions.

## Parameters

- **prepass-tolerance-km**: Tolerance passed to pair reduction (tested: 5, 10, 15, 20, 25, 30 km)
- **tolerance-km**: Coarse sweep detection threshold (swept from 50 to 400 km, incremented by 50)
- **step-second-ratio**: Fixed at 10
- **interpolation-stride**: Fixed at 6
- **lookahead-hours**: Fixed at 24 hours
- **threshold-km**: Final conjunction threshold (fixed at 5.0 km)

## Analysis

### Conjunction Count by Prepass Tolerance

| Prepass (km) | Avg Conj | Conj Range  | Miss % | Speed vs 30 km |
|--------------|----------|-------------|--------|----------------|
| 5.0          | 14734    | 14716-14740 | 12.48% | +40.1%         |
| 10.0         | 16670    | 16648-16677 | 0.98%  | +24.8%         |
| 15.0         | 16766    | 16744-16773 | 0.41%  | +18.5%         |
| 20.0         | 16803    | 16781-16810 | 0.19%  | +9.6%          |
| 25.0         | 16818    | 16796-16825 | 0.10%  | +5.7%          |
| 30.0         | 16827    | 16805-16834 | 0.04%  | baseline       |

At prepass=5.0 km, we detect only ~14,734 conjunctions versus ~16,827 at prepass=30.0 km. The pair reduction filter is
too aggressive and excludes satellite pairs that do have close approaches - missing 2,093 conjunctions (12.48%).

### Convergence Behavior

Conjunction count shows rapid convergence as pre-pass tolerance increases:

- **5.0 km**: 12.48% miss rate (~2,093 conjunctions missed) - unacceptable for operational use
- **10.0 km**: 0.98% miss rate (~157 conjunctions missed) - good balance, 24.8% faster than full coverage
- **15.0 km**: 0.41% miss rate (~61 conjunctions missed) - high confidence, 18.5% faster
- **20.0+ km**: <0.2% miss rate - essentially complete coverage

## Conclusion

- **Quick scans**: Prepass 10 km (99.02% accuracy, 24.8% faster)
- **Complete detection**: Prepass >=15 km (>99.5% accuracy)

![Total Processing Time](2-conjunction-prepass/1_total_time.png)

![Time Breakdown Stacked](2-conjunction-prepass/2_time_breakdown_stacked.png)

![Conjunctions Detected](2-conjunction-prepass/3_conjunctions.png)

## Running the Benchmark

```bash
# Run on Linux
./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-conjunction

# Run on Windows
./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-conjunction"

# *you must have a running PostgreSQL instance with the satellite catalog loaded, and orekit data files at src/main/resources/orekit-data
```
