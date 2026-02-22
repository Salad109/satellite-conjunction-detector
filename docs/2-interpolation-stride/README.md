# Interpolation Stride Sweep

The interpolation stride controls how many coarse time steps share a single SGP4 call. At stride=1, every step gets a
real SGP4 propagation. At stride=N, SGP4 is called every N steps and intermediate positions are filled by cubic Hermite
interpolation using position + velocity at each knot point.

## Parameters

- **tolerance-km**: Fixed at 64 km
- **cell-ratio**: Fixed at 1.30
- **step-second-ratio**: Fixed at 10 (step = 6.40s)
- **lookahead-hours**: Fixed at 24
- **threshold-km**: Fixed at 5.0 km
- **interpolation-stride**: Swept 1-200 in steps of 5
- **iterations**: 3 per configuration

## Results

Selected values (full data in csv):

| Stride | SGP4 Interval | Conjunctions | Accuracy | Loss  | Mean Time |
|--------|---------------|--------------|----------|-------|-----------|
| 1      | 6.40s         | 33,472       | 99.99%   | 0.01% | 322.9s    |
| 5      | 32.00s        | 33,472       | 99.99%   | 0.01% | 84.6s     |
| 10     | 64.00s        | 33,473       | 99.99%   | 0.01% | 55.9s     |
| 15     | 96.00s        | 33,473       | 99.99%   | 0.01% | 46.2s     |
| 20     | 128.00s       | 33,470       | 99.98%   | 0.02% | 40.3s     |
| 25     | 160.00s       | 33,472       | 99.99%   | 0.01% | 38.3s     |
| 30     | 192.00s       | 33,476       | 100.00%  | 0.00% | 36.8s     |
| 50     | 320.00s       | 33,472       | 99.99%   | 0.01% | 32.0s     |
| 75     | 480.00s       | 33,448       | 99.92%   | 0.08% | 29.5s     |
| 100    | 640.00s       | 33,402       | 99.78%   | 0.22% | 29.2s     |
| 150    | 960.00s       | 33,102       | 98.88%   | 1.12% | 27.7s     |
| 200    | 1280.00s      | 32,446       | 96.92%   | 3.08% | 27.3s     |

Speed gains flatten out around stride=50 because SGP4 time becomes negligible relative to check pairs and grouping.
Accuracy is noisy at high strides, but the overall trend past stride=100 is rapid degradation.

![Total Processing Time](1_total_time.png)

![Time Breakdown](2_time_breakdown.png)

![Time Breakdown Stacked](3_time_breakdown_stacked.png)

![Conjunctions Detected](4_conjunctions.png)

## Recommended Values

- **Fast** (>= 98% accuracy): stride = 170
- **Balanced** (>= 99% accuracy): stride = 130
- **Conservative** (>= 99.9% accuracy): stride = 50
