# Pareto Frontier: Speed vs Accuracy

The previous benchmarks (docs 1-4) swept one parameter at a time while holding the others at safe defaults. This
benchmark sweeps all three simultaneously using a bounded grid search to find the Pareto frontier of speed vs accuracy.
The key finding is that picking the 99.9% accuracy option for each parameter individually does NOT produce 99.9%
accuracy when all three are changed together. The losses interact and compound in unexpected ways.

## Setup

- **Satellite catalog**: 30100 objects
- **Lookahead**: 24 hours from a fixed start time
- **Collision threshold**: 5.0 km
- **Tolerance**: Fixed at 72 km
- **Iterations**: 3 per configuration, median time

## Swept Parameters

| Parameter                | Start | Delta |
|--------------------------|-------|-------|
| **step-second-ratio**    | 9     | 1     |
| **interpolation-stride** | 5     | 5     |
| **cell-ratio**           | 1.0   | 0.1   |

## Pareto Frontier

| Step | Stride | Cell | Cell (km) | Conj  | Accuracy | Time   |
|------|--------|------|-----------|-------|----------|--------|
| 9    | 25     | 1.00 | 72.0      | 43873 | 100.00%  | 29.77s |
| 9    | 25     | 1.20 | 60.0      | 43872 | 100.00%  | 29.36s |
| 9    | 25     | 1.30 | 55.4      | 43870 | 99.99%   | 28.79s |
| 8    | 25     | 1.00 | 72.0      | 43869 | 99.99%   | 26.52s |
| 8    | 30     | 1.10 | 65.5      | 43865 | 99.98%   | 24.34s |
| 8    | 30     | 1.20 | 60.0      | 43861 | 99.97%   | 24.34s |
| 8    | 40     | 1.10 | 65.5      | 43854 | 99.96%   | 23.44s |
| 8    | 40     | 1.20 | 60.0      | 43850 | 99.95%   | 23.29s |
| 8    | 40     | 1.30 | 55.4      | 43823 | 99.89%   | 22.99s |
| 8    | 50     | 1.10 | 65.5      | 43801 | 99.84%   | 22.47s |
| 8    | 60     | 1.30 | 55.4      | 43652 | 99.50%   | 22.33s |
| 8    | 50     | 1.50 | 48.0      | 43621 | 99.43%   | 20.94s |
| 8    | 70     | 1.40 | 51.4      | 43335 | 98.77%   | 20.80s |
| 7    | 35     | 1.20 | 60.0      | 43317 | 98.73%   | 20.70s |
| 7    | 40     | 1.20 | 60.0      | 43288 | 98.67%   | 20.38s |
| 7    | 50     | 1.00 | 72.0      | 43219 | 98.51%   | 20.25s |
| 7    | 40     | 1.30 | 55.4      | 43205 | 98.48%   | 20.15s |
| 7    | 50     | 1.20 | 60.0      | 43182 | 98.42%   | 20.08s |
| 7    | 50     | 1.30 | 55.4      | 43100 | 98.24%   | 19.85s |
| 7    | 45     | 1.10 | 65.5      | 43098 | 98.23%   | 19.81s |
| 7    | 50     | 1.40 | 51.4      | 42979 | 97.96%   | 19.09s |

These are the actual configurations to be considered for production use.

![Pareto Frontier](1_pareto_frontier.png)

![Frontier Parameter Evolution](2_frontier_parameters.png)

![Time Breakdown](3_time_breakdown.png)

![Time Breakdown Stacked](4_time_breakdown_stacked.png)
