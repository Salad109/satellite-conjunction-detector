# Pair Reduction Benchmark

With ~30,000 tracked objects, the naive approach would check over 400 million satellite pairs. The system applies
several sequential filters to reduce computational load.

## Results

| Strategy                        |   Unique Pairs | % of Original |      Time |  Throughput/sec |
|---------------------------------|---------------:|--------------:|----------:|----------------:|
| Full set (29,583 satellites)    |    438,361,245 |        100.0% |         - |               - |
| Skip debris on debris           |    191,756,736 |        43.74% |     1.84s |     237,664,556 |
| Only overlapping apogee/perigee |     82,686,395 |        18.86% |     1.68s |     260,628,008 |
| Intersecting orbital planes     |     26,804,778 |         6.11% |    23.01s |      19,052,274 |
| **All strategies combined**     | **17,110,639** |     **3.90%** | **4.13s** | **106,042,788** |

## Filter Details

### Skip debris on debris

Excludes pairs where both objects are debris. Debris-on-debris conjunctions are of low importance since neither object
can maneuver.

### Only overlapping apogee/perigee

Two satellites can only collide if their orbital altitude ranges overlap. Compares perigee/apogee of two satellites with
a tolerance buffer.

### Intersecting orbital planes

Non-coplanar orbits only intersect at two points (the line of nodes). The filter checks if the orbital radii at these
intersection points are within tolerance.

## Running the Benchmark

```bash
# Run on Linux
./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-filter

# Run on Windows
./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-filter"
```
