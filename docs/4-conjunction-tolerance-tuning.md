# Conjunction Tolerance Tuning

Final tuning of the coarse sweep tolerance parameter, with prepass and step ratio already optimized.

## Parameters

- **prepass-tolerance-km**: Fixed at 12.5 km (from prepass tuning)
- **step-second-ratio**: Fixed at 12 (from step ratio tuning)
- **tolerance-km**: Coarse detection threshold (swept from 120 to 1200 km in steps of 12)
- **lookahead-hours**: Fixed at 6 hours
- **threshold-km**: Final conjunction threshold (fixed at 5.0 km)

## Results

Benchmark on 25% satellite sample (7,397 satellites). Selected data.

| Tol (km) | Step (s) | Detections | Events | Conj | Dedup | Coarse | Refine | Total     |
|----------|----------|------------|--------|------|-------|--------|--------|-----------|
| 120      | 10       | 212K       | 51K    | 225  | 205   | 38.6s  | 1.5s   | 40.5s     |
| 180      | 15       | 309K       | 83K    | 224  | 204   | 26.3s  | 2.5s   | 29.2s     |
| 240      | 20       | 407K       | 112K   | 225  | 205   | 20.2s  | 3.7s   | 24.4s     |
| 300      | 25       | 507K       | 143K   | 223  | 203   | 16.2s  | 4.7s   | 21.5s     |
| 360      | 30       | 603K       | 174K   | 225  | 205   | 13.7s  | 5.6s   | 20.0s     |
| 420      | 35       | 695K       | 204K   | 226  | 206   | 12.1s  | 7.3s   | 20.0s     |
| 468      | 39       | 763K       | 227K   | 224  | 204   | 10.8s  | 7.8s   | 19.3s     |
| 480      | 40       | 783K       | 233K   | 227  | 206   | 10.4s  | 8.2s   | 19.4s     |
| 504      | 42       | 821K       | 244K   | 227  | 206   | 10.1s  | 8.4s   | **19.2s** |
| 540      | 45       | 883K       | 262K   | 226  | 206   | 9.3s   | 9.2s   | 19.2s     |
| 600      | 50       | 982K       | 293K   | 225  | 205   | 8.4s   | 10.8s  | 20.0s     |
| 720      | 60       | 1.2M       | 353K   | 226  | 206   | 7.0s   | 13.1s  | 21.0s     |
| 840      | 70       | 1.4M       | 414K   | 224  | 204   | 6.3s   | 15.7s  | 23.0s     |
| 960      | 80       | 1.6M       | 477K   | 229  | 209   | 5.4s   | 18.3s  | 24.8s     |
| 1080     | 90       | 1.8M       | 543K   | 225  | 205   | 4.9s   | 20.5s  | 26.5s     |
| 1200     | 100      | 2.0M       | 603K   | 226  | 205   | 4.4s   | 24.6s  | 30.2s     |

## Analysis

### Performance Curve

The total time shows a U-shaped curve:

- **Low tolerance (120-300 km)**: Coarse sweep dominates. Many time steps needed, but few detections to refine.
- **Optimal range (468-540 km)**: Balance between coarse and refine. Minimum total time ~19.2.
- **High tolerance (600+ km)**: Refine dominates. Fewer coarse steps, but more events to refine.

### Conjunction Stability

All configurations detect 203-209 deduplicated conjunctions, confirming that tolerance affects only performance, not
detection accuracy (given proper prepass and step ratio settings).

## Conclusion

**Optimal tolerance is 504 km with step seconds equal to 42s**

The sweet spot is where the sum of coarse and refine stage is the lowest. Going lower wastes time on excessive coarse
iterations; going higher wastes time refining too many false positives.
