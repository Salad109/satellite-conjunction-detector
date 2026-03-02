# Pareto Frontier: Speed vs Accuracy

The previous benchmarks (docs 1-3) swept one parameter at a time while holding the others at safe defaults. This
benchmark sweeps all three simultaneously using a bounded grid search to find the Pareto frontier of speed and accuracy.
The key finding is that picking the 99.9% accuracy option for each parameter individually does NOT produce 99.9%
accuracy when all three are shifted together. The losses interact and compound in unexpected ways.

## Parameters

- **tolerance-km**: Fixed at 72 km
- **lookahead-hours**: Fixed at 24
- **threshold-km**: Fixed at 5.0 km
- **step-second-ratio**: Swept from 9 down, delta 1
- **interpolation-stride**: Swept from 10 up, delta 10
- **cell-ratio**: Swept from 1.0 up, delta 0.15
- **iterations**: 2 per configuration, averaged

## Pareto Frontier

| Step | Stride | Cell | Cell (km) | Conj  | Accuracy | Time   |
|------|--------|------|-----------|-------|----------|--------|
| 9    | 20     | 1.30 | 55.4      | 37022 | 100.00%  | 32.56s |
| 8    | 20     | 1.00 | 72.0      | 37021 | 100.00%  | 29.72s |
| 8    | 20     | 1.15 | 62.6      | 37019 | 99.99%   | 29.34s |
| 9    | 30     | 1.15 | 62.6      | 37015 | 99.98%   | 29.26s |
| 9    | 30     | 1.30 | 55.4      | 37014 | 99.98%   | 28.94s |
| 8    | 30     | 1.00 | 72.0      | 37012 | 99.97%   | 26.01s |
| 8    | 30     | 1.15 | 62.6      | 37010 | 99.97%   | 25.71s |
| 8    | 40     | 1.00 | 72.0      | 36992 | 99.92%   | 24.49s |
| 8    | 40     | 1.15 | 62.6      | 36990 | 99.91%   | 23.81s |
| 8    | 40     | 1.30 | 55.4      | 36958 | 99.83%   | 23.59s |
| 8    | 50     | 1.15 | 62.6      | 36938 | 99.77%   | 23.02s |
| 8    | 50     | 1.30 | 55.4      | 36907 | 99.69%   | 22.53s |
| 8    | 60     | 1.15 | 62.6      | 36831 | 99.48%   | 22.34s |
| 8    | 50     | 1.45 | 49.7      | 36807 | 99.42%   | 22.03s |
| 8    | 60     | 1.30 | 55.4      | 36801 | 99.40%   | 21.76s |
| 8    | 60     | 1.45 | 49.7      | 36705 | 99.14%   | 21.30s |
| 8    | 60     | 1.60 | 45.0      | 36532 | 98.68%   | 21.20s |
| 7    | 40     | 1.15 | 62.6      | 36424 | 98.38%   | 21.00s |
| 7    | 50     | 1.00 | 72.0      | 36340 | 98.16%   | 20.44s |
| 7    | 50     | 1.30 | 55.4      | 36201 | 97.78%   | 20.17s |
| 7    | 60     | 1.00 | 72.0      | 36122 | 97.57%   | 20.01s |

These are the actual configurations to be considered for production use.

![Pareto Frontier](1_pareto_frontier.png)

![Frontier Parameter Evolution](2_frontier_parameters.png)
