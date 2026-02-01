# Step Second Ratio Tuning

The coarse sweep samples satellite positions at fixed time intervals. The step size must be small enough that
fast-moving objects don't "skip over" each other between samples. With LEO satellites reaching relative velocities up
to ~15 km/s, the theoretical safe ratio is:

```
step_seconds = (int) (tolerance_km / 15)  // truncates to integer
```

This experiment tests whether different step sizes (ratios 6-15) affect detected conjunction count and processing time.

## Parameters

- **prepass-tolerance-km**: Fixed at 10.0 km (from prepass tuning experiment)
- **step-second-ratio**: Divides tolerance by this value to get step size (tested: 6, 7, 8, 9, 10, 11, 13, 15)
- **tolerance-km**: Coarse detection threshold (swept from 50 to 400 km, incremented by 50)
- **interpolation-stride**: Fixed at 6
- **lookahead-hours**: Fixed at 24 hours
- **threshold-km**: Final conjunction threshold (fixed at 5.0 km)

## Analysis

### Conjunction Count by Step Ratio

| Ratio | Avg Conj | Conj Range  | Accuracy | Speed vs Ratio 10 |
|-------|----------|-------------|----------|-------------------|
| 6     | 15763    | 15676-15917 | 94.56%   | +20.9%            |
| 7     | 16553    | 16462-16600 | 99.30%   | +14.3%            |
| 8     | 16656    | 16616-16677 | 99.92%   | +8.7%             |
| 9     | 16658    | 16629-16677 | 99.93%   | +3.2%             |
| 10    | 16670    | 16648-16677 | 100.00%  | baseline          |
| 11    | 16665    | 16650-16677 | 99.97%   | -5.7%             |
| 13    | 16667    | 16647-16676 | 99.98%   | -16.9%            |
| 15    | 16668    | 16645-16675 | 99.99%   | -26.5%            |

Ratio 10 detects the maximum conjunctions. Higher ratios provide no detection benefit while significantly increasing
runtime.

### Convergence Behavior

Conjunction count shows rapid convergence by ratio 8:

- **Ratio 6**: 94.56% accuracy - too aggressive
- **Ratio 7**: 99.30% accuracy (~117 conjunctions missed) - 14.3% faster
- **Ratio 8-10**: >99.9% accuracy - essentially complete detection

Going beyond ratio 10 provides zero detection benefit while making the scan slower.

## Conclusion

- **Quick scans**: Prepass 10 km + Ratio 7 (98.33% accuracy, maximum speed)
- **Complete detection**: Prepass >=15 km + Ratio 10 (99.59% accuracy)

![Total Processing Time](3-conjunction-step-ratio/1_total_time.png)

![Time Breakdown Stacked](3-conjunction-step-ratio/2_time_breakdown_stacked.png)

![Conjunctions Detected](3-conjunction-step-ratio/3_conjunctions.png)

## Running the Benchmark

```bash
# Run on Linux
./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-conjunction

# Run on Windows
./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-conjunction"

# *you must have a running PostgreSQL instance with the satellite catalog loaded, and orekit data files at src/main/resources/orekit-data
```
