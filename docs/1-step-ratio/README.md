# Step Second Ratio Sweep

The step second ratio determines the coarse scan time step: `step_seconds = tolerance_km / ratio`. Higher ratio means
smaller steps (more SGP4 calls, slower). Lower ratio means larger steps (faster but high velocity conjunctions between
steps can be missed). With maximum relative velocity of 15 km/s between satellites, the theoretical safe step is
`tolerance_km / 15 km/s = 64 km / 15 km/s = 4.27 s` (ratio=15). We sweep from ratio=6 (10.67s step) to ratio=15 (4.27s
step) to find the sweet spot between speed and accuracy.

## Parameters

- **tolerance-km**: Fixed at 64 km
- **cell-ratio**: Fixed at 1.30
- **interpolation-stride**: Fixed at 30
- **lookahead-hours**: Fixed at 24
- **threshold-km**: Fixed at 5.0 km
- **step-second-ratio**: Swept 6-15
- **iterations**: 3 per configuration

## Results

| Ratio | Step (s) | Conjunctions | Accuracy | Loss  | Mean Time |
|-------|----------|--------------|----------|-------|-----------|
| 6     | 10.67    | 30,688       | 91.67%   | 8.33% | 21.6s     |
| 7     | 9.14     | 32,837       | 98.09%   | 1.91% | 25.1s     |
| 8     | 8.00     | 33,447       | 99.91%   | 0.09% | 28.1s     |
| 9     | 7.11     | 33,469       | 99.98%   | 0.02% | 31.7s     |
| 10    | 6.40     | 33,476       | 100.00%  | 0.00% | 35.8s     |
| 11    | 5.82     | 33,476       | 100.00%  | 0.00% | 38.5s     |
| 12    | 5.33     | 33,473       | 99.99%   | 0.01% | 42.9s     |
| 13    | 4.92     | 33,471       | 99.99%   | 0.01% | 46.4s     |
| 14    | 4.57     | 33,473       | 99.99%   | 0.01% | 50.0s     |
| 15    | 4.27     | 33,472       | 99.99%   | 0.01% | 53.7s     |

Ratio=10 and 15 detect practically the same conjunctions. The extra resolution at ratio=15 costs 1.5x more time for zero
benefit.

![Total Processing Time](1_total_time.png)

![Time Breakdown](2_time_breakdown.png)

![Time Breakdown Stacked](3_time_breakdown_stacked.png)

![Conjunctions Detected](4_conjunctions.png)

## Recommended Values

- **Fast** (>= 98% accuracy): ratio = 7
- **Balanced** (>= 99.9% accuracy): ratio = 8
- **Conservative** (100% accuracy): ratio = 10
