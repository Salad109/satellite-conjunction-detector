# Conjunction Tolerance Tuning

Final tuning of the coarse sweep tolerance parameter, with prepass, step ratio, and interpolation already optimized.

## Parameters

- **prepass-tolerance-km**: Fixed at 12.5 km (from prepass tuning)
- **step-second-ratio**: Fixed at 12 (from step ratio tuning)
- **interpolation-stride**: Fixed at 6 (from interpolation tuning)
- **tolerance-km**: Coarse detection threshold (swept from 60 to 1200 km in steps of 12)
- **lookahead-hours**: Fixed at 6 hours
- **threshold-km**: Final conjunction threshold (fixed at 5.0 km)

## Results

Benchmark on 25% satellite sample (7,397 satellites). Selected data points.

| Tol (km) | Step (s) | Detections | Events | Conj | Dedup | Coarse | Refine | Total     |
|----------|----------|------------|--------|------|-------|--------|--------|-----------|
| 60       | 5        | 118K       | 23K    | 230  | 207   | 7.88s  | 0.37s  | 9.25s     |
| 84       | 7        | 147K       | 34K    | 224  | 204   | 5.29s  | 0.64s  | 6.97s     |
| 108      | 9        | 194K       | 45K    | 228  | 208   | 4.11s  | 0.88s  | 5.97s     |
| 132      | 11       | 232K       | 58K    | 225  | 205   | 3.29s  | 1.12s  | 5.41s     |
| 156      | 13       | 270K       | 71K    | 227  | 207   | 2.68s  | 1.43s  | 5.03s     |
| 180      | 15       | 309K       | 83K    | 229  | 206   | 2.31s  | 1.66s  | 4.93s     |
| 192      | 16       | 327K       | 89K    | 231  | 210   | 2.22s  | 1.70s  | **4.89s** |
| 204      | 17       | 344K       | 94K    | 227  | 207   | 2.00s  | 1.87s  | 4.89s     |
| 216      | 18       | 364K       | 100K   | 227  | 207   | 1.96s  | 1.94s  | 4.90s     |
| 240      | 20       | 408K       | 112K   | 229  | 208   | 1.73s  | 2.34s  | 5.05s     |
| 300      | 25       | 509K       | 143K   | 229  | 208   | 1.42s  | 2.77s  | 5.22s     |
| 360      | 30       | 607K       | 175K   | 225  | 206   | 1.14s  | 3.59s  | 5.76s     |
| 480      | 40       | 791K       | 234K   | 229  | 209   | 0.90s  | 4.87s  | 6.89s     |
| 600      | 50       | 999K       | 296K   | 228  | 208   | 0.74s  | 6.16s  | 7.96s     |
| 720      | 60       | 1.2M       | 359K   | 225  | 204   | 0.64s  | 7.86s  | 9.62s     |
| 840      | 70       | 1.4M       | 419K   | 221  | 202   | 0.56s  | 9.00s  | 10.67s    |
| 960      | 80       | 1.7M       | 489K   | 224  | 204   | 0.49s  | 10.79s | 12.37s    |
| 1080     | 90       | 1.9M       | 561K   | 219  | 199   | 0.43s  | 13.02s | 14.60s    |
| 1200     | 100      | 2.1M       | 625K   | 222  | 203   | 0.41s  | 14.68s | 16.35s    |

## Analysis

### Time Complexity Trade-off

The coarse and refine stages have opposing time complexities with respect to tolerance:

- **Coarse time - O(1/tolerance)**: Larger tolerance means larger step size, so fewer propagation calls. Doubling
  tolerance roughly halves coarse time.
- **Refine time - O(tolerance)**: Larger tolerance catches more events that need refinement.

Since total time = coarse + refine + negligible overhead, the optimal tolerance minimizes this sum. As tolerance
increases, coarse time decreases but refine time increases. The U-shaped total time curve has its minimum where these
opposing effects balance.

### Performance Curve

| Tolerance Range | Dominant Stage | Behavior                                 |
|-----------------|----------------|------------------------------------------|
| 60-150 km       | Coarse         | Many time steps, few events to refine    |
| 180-220 km      | **Balanced**   | Coarse ≈ Refine, minimum total time      |
| 240+ km         | Refine         | Fewer coarse steps, many false positives |

### Conjunction Stability

Configurations up to ~800 km detect 201-210 deduplicated conjunctions consistently. Above 800 km, detection begins to
drop off slightly (199 at 1080 km). This is likely due to interpolation error at stride=6 compounding with large step
sizes, but it's irrelevant in practice since optimal tolerance is well below this range.

## Conclusion

**Optimal tolerance is 192 km with step size of 16s**

The optimal tolerance is where coarse and refine times are balanced. Going lower wastes time on excessive coarse
iterations; going higher wastes time refining too many false positives.
