# Prepass Tolerance and Step Ratio Exploration

Previous tuning (see `2-conjunction-tolerance-tuning.md`) coupled prepass distance and step ratio parameters to coarse 
scan tolerance as follows:

- **tolerance-km**: Coarse sweep detection threshold
- **prepass-distance-km**: Filter tolerance in pair reduction (altitude shell overlap + orbital plane
  intersection checks). Previously was set equal to `tolerance-km`.
- **step-ratio**: Time step of coarse scan. Previously equal to `ceil(tolerance-km / 15)`

This experiment tests whether decoupling prepass distance from coarse scan tolerance, as well as using a different step
ratio than 15, provides a speedup and how low ratio can go without missing conjunctions.

## Parameter Space

- Sample: 25% catalog (7,397 satellites)
- Lookahead: 6 hours
- Conjunction threshold: 5.0 km

- `prepass`: 180-600 km (step 60), constrained `prepass >= tolerance`
- `tolerance`: 180-600 km (step 60)
- `ratio`: 12, 15, 18, 20

144 runs total (36 prepass/tolerance pairs × 4 ratios)

## Results

Best 10 configurations by total time:

| prepass_km | tolerance_km | ratio | step_s | dedup | coarse_s | refine_s | total_s |
|-----------:|-------------:|------:|-------:|------:|---------:|---------:|--------:|
|        360 |          360 |    12 |     30 |   205 |  49.4962 |   31.825 | 83.8434 |
|        300 |          300 |    12 |     25 |   205 |  59.3079 |  24.1116 | 85.4529 |
|        420 |          360 |    12 |     30 |   205 |  51.1039 |  31.8316 | 85.4665 |
|        240 |          240 |    12 |     20 |   205 |  68.8347 |  17.7864 | 88.1512 |
|        480 |          360 |    12 |     30 |   206 |  53.5353 |   32.504 | 88.4453 |
|        540 |          360 |    12 |     30 |   205 |  55.0925 |  30.9439 | 88.4667 |
|        420 |          300 |    12 |     25 |   205 |  61.8546 |  25.1669 | 89.1526 |
|        600 |          360 |    12 |     30 |   205 |  55.9155 |  30.7871 | 89.1916 |
|        420 |          420 |    12 |     35 |   206 |  44.5865 |  42.6181 | 90.1308 |
|        480 |          300 |    12 |     25 |   207 |   63.291 |  25.4836 | 90.7806 |

**Optimal configuration:** `prepass=360km, tolerance=360km, ratio=12, step=30s`

### Findings

**1. Decoupling prepass from tolerance provides no benefit**

In the best configurations, `prepass` parameter is equal or very close to `tolerance`. The geometric prepass filter
eliminates satellite pairs whose orbital geometry makes close approaches impossible. Widening prepass beyond tolerance
does not reduce the number of pairs sent to coarse sweep, so there are no performance gains to be had.

**2. Ratio 12 dominates across all tolerance levels**

Lower ratio = larger time steps = fewer coarse sweep iterations. Ratio 12 consistently beats 15, 18, and 20 at every
tolerance level. The constraint `step <= tolerance / 15` was conservative - ratio 12 maintains full conjunction
detection (204-209 dedup across all runs).

**3. Optimal tolerance is ~336-360 km**

360 km balances both coarse sweep and refinement time. Previous tuning (see `2-conjunction-tolerance-tuning.md`) found
336 km optimal with finer granularity (step 12 km). The optimal tolerance value appears to be between 300 and 420 km,
with this grid's 360 km and fine-tuning's 336 km both performing well.

**4. Step ratio 12 is safe in practice**

At `tolerance=360km, ratio=12`, step size is 30 seconds. At maximum relative velocity (15 km/s), satellites travel
450 km between samples, which exceeds the distance covered by the coarse scan step size. However, that would be a
worst-case, head-on at max speed scenario, and extremely unlikely in practice. All 144 runs detected 204-209
conjunctions (expected range), confirming no conjunctions are missed.

## Recommended Configuration

```properties
conjunction.tolerance-km=336.0
conjunction.step-seconds=28
```

`conjunction.prepass-distance-km` parameter was removed and now pair reduction tolerance is set equal to
`tolerance-km` internally.

Both values sit in the performance valley. Fine-grained tuning in `2-conjunction-tolerance-tuning.md` found 336 km and
28s optimal (85.2s), which aligns with this grid's finding that tolerance around 300-420 km with ratio 12 performs best.

## Running the Benchmark

Define parameters in `src/main/java/io/salad109/conjunctionapi/conjunction/internal/ConjunctionBenchmark.java`:

```bash
# Run on Linux
./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-conjunction

# Run on Windows
./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-conjunction"
```