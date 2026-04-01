# Benchmark Experiments

Each subdirectory is a benchmark experiment with a writeup, CSV results, and plot scripts. Experiments 1-4 sweep one
parameter at a time. Experiment 5 sweeps all three together. Experiments 6-7 cover runtime configuration.

| # | Experiment                                        | What it sweeps                                                              |
|---|---------------------------------------------------|-----------------------------------------------------------------------------|
| 1 | [Step Ratio](1-step-ratio/)                       | Time step size via `step_seconds = tolerance_km / ratio`                    |
| 2 | [Interpolation Stride](2-interpolation-stride/)   | SGP4 calls per time step via Hermite interpolation spacing                  |
| 3 | [Cell Size Ratio](3-cell-size-ratio/)             | Spatial grid cell size via `cell_size_km = tolerance_km / ratio`            |
| 4 | [Conjunction Tolerance](4-conjunction-tolerance/) | Coarse scan distance threshold in km                                        |
| 5 | [Pareto Frontier](5-pareto-frontier/)             | All three parameters simultaneously (grid search)                           |
| 6 | [Garbage Collector](6-gc/)                        | G1 vs Parallel vs Shenandoah vs ZGC throughput                              |
| 7 | [Subwindow Count](7-subwindow-count/)             | PositionCache memory via sequential subwindows (writeup only, no benchmark) |
