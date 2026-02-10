# Garbage Collector Selection

This experiment compares G1GC (default), ZGC, Parallel GC, and Shenandoah for conjunction detection workloads.

## Parameters

- **prepass-tolerance-km**: Fixed at 10.0 km
- **step-second-ratio**: Fixed at 10
- **interpolation-stride**: Fixed at 6
- **tolerance-km**: Swept from 50 to 400 km, incremented by 25
- **lookahead-hours**: Fixed at 24 hours
- **threshold-km**: Fixed at 5.0 km

## Results

Full satellite catalog (~30,000 satellites). Fastest run for each GC:

| GC         | Best Time | Tolerance | Propagate | Check  | Refine | vs G1GC  |
|------------|-----------|-----------|-----------|--------|--------|----------|
| G1GC       | 48.26s    | 200 km    | 9.87s     | 6.02s  | 14.58s | baseline |
| Shenandoah | 51.80s    | 125 km    | 16.77s    | 9.46s  | 9.41s  | +7.3%    |
| Parallel   | 54.26s    | 150 km    | 16.50s    | 7.75s  | 13.99s | +12.4%   |
| ZGC        | 55.16s    | 150 km    | 13.94s    | 10.95s | 12.47s | +14.3%   |

## Conclusion

**G1GC (default) is the best collector for conjunction detection workloads.**

G1GC is 7-14% faster than all alternative collectors tested. At optimal tolerance (200 km), G1GC achieves 48.26s total
time compared to 51.80s for Shenandoah (+7.3%), 54.26s for Parallel (+12.4%), and 55.16s for ZGC (+14.3%).

All GCs detect identical conjunctions. GC choice affects performance, not correctness.

![Total Processing Time](5-gc/1_total_time.png)

![Time Breakdown Stacked](5-gc/2_time_breakdown_stacked.png)

![Conjunctions Detected](5-gc/3_conjunctions.png)
