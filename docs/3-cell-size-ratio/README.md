# Cell Size Ratio Sweep

The spatial grid divides 3D space into cubic cells for neighbor lookup. `cell_size_km = tolerance_km / cell_ratio`.
Smaller cells mean fewer satellites per cell, so each satellite has fewer candidates to check against. But
if cells are too small relative to the tolerance, two satellites within tolerance can end up in non-adjacent cells and
never get compared. The sweep finds where that tradeoff breaks.

## Parameters

- **tolerance-km**: Fixed at 64 km
- **step-second-ratio**: Fixed at 10 (step = 6.40s)
- **interpolation-stride**: Fixed at 30
- **lookahead-hours**: Fixed at 24
- **threshold-km**: Fixed at 5.0 km
- **cell-ratio**: Swept 0.50-2.00 in steps of 0.10
- **iterations**: 3 per configuration

## Results

| Cell Ratio | Cell Size (km) | Conjunctions | Accuracy | Loss  | Mean Time | Check Time |
|------------|----------------|--------------|----------|-------|-----------|------------|
| 0.50       | 128.0          | 33,476       | 100.00%  | 0.00% | 41.9s     | 25.1s      |
| 0.70       | 91.4           | 33,476       | 100.00%  | 0.00% | 37.4s     | 20.7s      |
| 1.00       | 64.0           | 33,476       | 100.00%  | 0.00% | 35.5s     | 19.0s      |
| 1.20       | 53.3           | 33,476       | 100.00%  | 0.00% | 34.6s     | 18.5s      |
| 1.30       | 49.2           | 33,476       | 100.00%  | 0.00% | 35.0s     | 18.4s      |
| 1.40       | 45.7           | 33,475       | 100.00%  | 0.00% | 34.1s     | 18.0s      |
| 1.50       | 42.7           | 33,465       | 99.97%   | 0.03% | 34.5s     | 17.8s      |
| 1.60       | 40.0           | 33,451       | 99.93%   | 0.07% | 33.8s     | 17.8s      |
| 1.70       | 37.6           | 33,420       | 99.83%   | 0.17% | 33.0s     | 17.1s      |
| 1.80       | 35.6           | 33,371       | 99.69%   | 0.31% | 31.9s     | 16.6s      |
| 1.90       | 33.7           | 33,291       | 99.45%   | 0.55% | 31.2s     | 16.1s      |
| 2.00       | 32.0           | 33,168       | 99.08%   | 0.92% | 30.9s     | 16.0s      |

Accuracy holds at 100% up to ratio=1.40 (cell=45.7 km) then degrades rapidly.

![Total Processing Time](1_total_time.png)

![Time Breakdown](2_time_breakdown.png)

![Time Breakdown Stacked](3_time_breakdown_stacked.png)

![Conjunctions Detected](4_conjunctions.png)

## Recommended Values

- **Fast** (>= 99% accuracy): ratio = 2.00 (cell = 32.0 km)
- **Balanced** (>= 99.9% accuracy): ratio = 1.60 (cell = 40.0 km)
- **Conservative** (100% accuracy): ratio = 1.30 (cell = 49.2 km)
