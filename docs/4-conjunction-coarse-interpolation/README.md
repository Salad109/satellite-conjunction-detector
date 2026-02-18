# Interpolation Stride Sweep

The interpolation stride controls how many coarse time steps share a single SGP4 call. At stride=1, every step gets a
real SGP4 propagation. At stride=N, SGP4 is called every N steps and intermediate positions are filled by cubic Hermite
interpolation using position + velocity at each knot point.

## Parameters

- **tolerance-km**: Fixed at 250 km
- **prepass-tolerance-km**: Fixed at 30 km
- **step-second-ratio**: Fixed at 10 (step = 25s)
- **lookahead-hours**: Fixed at 24
- **threshold-km**: Fixed at 5.0 km
- **interpolation-stride**: Swept {1, 4, 8, 12, 16, 24, 32, 48, 64}
- **iterations**: 3 per configuration

## Results

| Stride | SGP4 Interval | Conjunctions | Accuracy | Loss  | Mean Time |
|--------|---------------|--------------|----------|-------|-----------|
| 1      | 25s           | 26,112       | 100.00%  | 0.00% | 126.1s    |
| 4      | 100s          | 26,110       | 99.99%   | 0.01% | 68.3s     |
| 8      | 200s          | 26,112       | 100.00%  | 0.00% | 58.6s     |
| 12     | 300s          | 26,109       | 99.99%   | 0.01% | 55.3s     |
| 16     | 400s          | 26,102       | 99.96%   | 0.04% | 53.8s     |
| 24     | 600s          | 26,082       | 99.89%   | 0.11% | 51.6s     |
| 32     | 800s          | 25,950       | 99.38%   | 0.62% | 53.7s     |
| 48     | 1200s         | 25,551       | 97.85%   | 2.15% | 51.7s     |
| 64     | 1600s         | 25,105       | 96.14%   | 3.86% | 51.8s     |

Speed plateaus around stride=16-24 because SGP4 time becomes negligible and other stages (check pairs, grouping)
dominate. Accuracy holds up well through stride=24 (0.11% loss) then drops off past stride=32.

![Total Processing Time](1_total_time.png)

![Time Breakdown](2_time_breakdown_stacked.png)

![Conjunctions Detected](3_conjunctions.png)

## Recommended Values

- **Fast** (>= 97.8% accuracy): stride = 48
- **Balanced** (>= 99.9% accuracy): stride = 24
- **Conservative** (>= 99.96% accuracy): stride = 16
