# Interpolation Stride Tuning

SGP4 propagation is the most expensive operation in the entire conjunction scanning pipeline. The `interpolation-stride`
parameter reduces SGP4 calls during the coarse sweep phase by computing positions only at select points and linearly
interpolating between them.

## Parameters

- **interpolation-stride**: Stride between SGP4 computations
    - `1` = all positions computed via SGP4 (no interpolation)
    - `2` = every 2nd position is SGP4, others linearly interpolated
    - `6` = every 6th position is SGP4, 5/6 interpolated
- **prepass-tolerance-km**: Fixed at 15.0 km
- **step-second-ratio**: Fixed at 14
- **tolerance-km**: Swept from 120 to 1184 km
- **lookahead-hours**: Fixed at 24 hours
- **threshold-km**: Fixed at 5.0 km

## Analysis

### Speed vs Accuracy Trade-off

| Stride | Speed Gain | Miss Rate | Status       |
|--------|------------|-----------|--------------|
| 1      | 0.0%       | 0.0%      | Baseline     |
| 2      | 38.1%      | 0.0%      | Complete     |
| 4      | 54.3%      | 0.1%      | Complete     |
| **6**  | **58.5%**  | **0.1%**  | **Complete** |
| 7      | 58.0%      | 0.4%      | Missing ~5   |
| 8      | 61.5%      | 0.5%      | Missing ~7   |
| 10     | 63.0%      | 0.7%      | Missing ~10  |
| 12     | 62.1%      | 1.5%      | Missing ~20  |

### Convergence Behavior

**Stride 1-4**: Perfect or near-perfect detection (99.9%+ accuracy)

- Interpolation error negligible for these stride values
- Speed gains scale well

**Stride 6**: Sweet spot (99.9% accuracy, 58.5% faster)

- Coarse time reduced by 73.7% vs stride=1
- Only 2 conjunctions missed on average (~0.1%)
- SGP4 calls reduced by 83.3%

**Stride 7-8**: Marginal gains not worth accuracy loss

- Stride 7: 58.0% faster but 0.4% miss rate (not worth it over stride 6)
- Stride 8: 61.5% faster but 0.5% miss rate (3% more speed, 4x worse accuracy than stride 6)
- Diminishing returns begin here

**Stride 10+**: Unacceptable accuracy degradation

- Speed gains plateau
- Miss rates increase exponentially

## Conclusion

**Optimal interpolation stride is 6**

This reduces SGP4 calls by 83.3% and total runtime by 58.5% while preserving 99.9% of conjunctions. Stride 7-8 offer
only 3% additional speed but degrade accuracy by 4-5x (0.4-0.5% miss vs 0.1%). Not worth the trade-off.

![Total Processing Time](4-conjunction-coarse-interpolation/1_total_time.png)

![Coarse vs Refine Time](4-conjunction-coarse-interpolation/2_coarse_vs_refine.png)

![Time Breakdown](4-conjunction-coarse-interpolation/3_time_breakdown.png)

![Conjunctions Detected](4-conjunction-coarse-interpolation/4_conjunctions.png)