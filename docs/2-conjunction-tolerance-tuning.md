# Conjunction Detection Parameter Tuning

This document records tuning experiments for the coarse-fine conjunction detection algorithm.

## Parameters

- **tolerance-km**: Coarse detection threshold (also affects pair reduction geometry)
- **step-seconds**: Time step for coarse sweep. It must be <= tolerance-km / 15, since 15 km/s is approx. max relative
  speed of two LEO satellites
- **lookahead-hours**: How far ahead to scan (fixed at 6 hours for benchmarking)
- **threshold-km**: Final conjunction threshold (fixed at 5.0 km)

## Sample Size Comparison

The following table proves that results on a random 25% satellite sample (7,397 satellites) scale predictably to the
full catalog (29,586 satellites). Therefore, tuning can be done with confidence on the smaller sample for faster
iteration.

| Sats   | Tol (km) | Step (s) | Hours | Detections | Events | Conj  | Dedup | Coarse  | Refine | Total   |
|--------|----------|----------|-------|------------|--------|-------|-------|---------|--------|---------|
| 29,586 | 50       | 4        | 6     | 2.56M      | 563K   | 3,958 | 3,470 | 1223.4s | 27.1s  | 1256.9s |
| 7,397  | 50       | 4        | 6     | 155K       | 35K    | 222   | 202   | 318.7s  | 0.9s   | 319.7s  |

## Tuning Results

Benchmark results from 25% satellite catalog (7,397 satellites). Values are averaged over multiple runs.

| Config           | Detections | Events | Conj  | Dedup | Coarse | Refine | **Total** |
|------------------|------------|--------|-------|-------|--------|--------|-----------|
| tol=84, step=7   | 314K       | 96K    | 225.3 | 205.3 | 128.0s | 2.5s   | 131.1s    |
| tol=96, step=8   | 403K       | 125K   | 225.7 | 205.7 | 121.0s | 3.5s   | 125.1s    |
| tol=108, step=9  | 508K       | 156K   | 227.0 | 207.0 | 111.7s | 4.2s   | 116.6s    |
| tol=120, step=10 | 603K       | 188K   | 226.7 | 206.7 | 105.6s | 5.1s   | 111.5s    |
| tol=132, step=11 | 705K       | 223K   | 226.7 | 206.3 | 100.9s | 6.1s   | 107.9s    |
| tol=144, step=12 | 811K       | 258K   | 226.0 | 206.0 | 96.5s  | 7.2s   | 104.8s    |
| tol=156, step=13 | 915K       | 294K   | 227.0 | 206.7 | 90.4s  | 8.9s   | 100.3s    |
| tol=168, step=14 | 1.0M       | 330K   | 225.7 | 206.0 | 85.9s  | 10.0s  | 97.0s     |
| tol=180, step=15 | 1.1M       | 366K   | 225.4 | 205.6 | 79.9s  | 10.7s  | 91.9s     |
| tol=192, step=16 | 1.2M       | 402K   | 225.5 | 205.5 | 78.1s  | 11.8s  | 91.2s     |
| tol=204, step=17 | 1.3M       | 440K   | 225.8 | 205.6 | 76.8s  | 13.2s  | 91.4s     |
| tol=216, step=18 | 1.5M       | 479K   | 226.2 | 206.0 | 73.5s  | 14.5s  | 89.6s     |
| tol=228, step=19 | 1.6M       | 519K   | 226.0 | 205.8 | 72.4s  | 16.4s  | 90.5s     |
| tol=240, step=20 | 1.7M       | 560K   | 225.8 | 205.8 | 70.0s  | 18.6s  | 90.3s     |
| tol=252, step=21 | 1.8M       | 601K   | 225.9 | 205.9 | 66.9s  | 20.2s  | 89.0s     |
| tol=264, step=22 | 1.9M       | 641K   | 225.8 | 205.9 | 64.8s  | 20.9s  | 87.6s     |
| tol=276, step=23 | 2.0M       | 683K   | 225.1 | 205.3 | 63.0s  | 23.4s  | 88.4s     |
| tol=288, step=24 | 2.2M       | 725K   | 226.3 | 206.3 | 60.7s  | 24.2s  | 87.0s     |
| tol=300, step=25 | 2.3M       | 767K   | 225.7 | 205.8 | 59.3s  | 25.2s  | 86.7s     |
| tol=312, step=26 | 2.4M       | 809K   | 225.0 | 204.8 | 57.9s  | 26.6s  | 86.7s     |
| tol=324, step=27 | 2.5M       | 853K   | 225.3 | 205.3 | 57.7s  | 27.8s  | 87.9s     |
| tol=336, step=28 | 2.6M       | 896K   | 225.8 | 205.8 | 54.1s  | 28.6s  | **85.2s** |
| tol=348, step=29 | 2.7M       | 940K   | 225.9 | 205.7 | 52.7s  | 31.3s  | 86.5s     |
| tol=360, step=30 | 2.9M       | 985K   | 226.3 | 206.0 | 51.6s  | 32.3s  | 86.5s     |
| tol=372, step=31 | 3.0M       | 1.0M   | 225.6 | 205.3 | 50.7s  | 35.3s  | 88.7s     |
| tol=384, step=32 | 3.1M       | 1.1M   | 226.0 | 205.9 | 49.7s  | 35.5s  | 88.0s     |
| tol=396, step=33 | 3.2M       | 1.1M   | 227.3 | 207.1 | 48.6s  | 39.3s  | 90.8s     |
| tol=408, step=34 | 3.3M       | 1.2M   | 225.7 | 205.7 | 48.0s  | 39.3s  | 90.3s     |
| tol=420, step=35 | 3.5M       | 1.2M   | 224.7 | 204.3 | 47.3s  | 42.8s  | 93.1s     |
| tol=432, step=36 | 3.6M       | 1.3M   | 225.3 | 205.3 | 46.8s  | 43.7s  | 93.5s     |
| tol=444, step=37 | 3.7M       | 1.3M   | 226.0 | 206.0 | 45.0s  | 44.6s  | 92.8s     |
| tol=456, step=38 | 3.8M       | 1.4M   | 226.3 | 206.3 | 44.5s  | 45.7s  | 93.5s     |
| tol=468, step=39 | 4.0M       | 1.4M   | 226.3 | 206.3 | 43.6s  | 48.3s  | 95.3s     |
| tol=480, step=40 | 4.1M       | 1.5M   | 225.3 | 205.3 | 42.8s  | 50.2s  | 96.3s     |
| tol=492, step=41 | 4.2M       | 1.5M   | 225.3 | 205.3 | 42.4s  | 52.5s  | 98.3s     |
| tol=504, step=42 | 4.3M       | 1.5M   | 225.7 | 205.7 | 42.1s  | 53.6s  | 99.4s     |
| tol=516, step=43 | 4.5M       | 1.6M   | 225.7 | 205.7 | 40.7s  | 56.7s  | 101.1s    |
| tol=528, step=44 | 4.6M       | 1.6M   | 227.0 | 206.3 | 39.2s  | 56.2s  | 99.1s     |
| tol=540, step=45 | 4.7M       | 1.7M   | 225.3 | 205.3 | 38.6s  | 59.3s  | 101.8s    |
| tol=552, step=46 | 4.9M       | 1.8M   | 226.0 | 206.0 | 38.0s  | 62.1s  | 104.1s    |
| tol=564, step=47 | 5.0M       | 1.8M   | 224.7 | 204.7 | 39.1s  | 65.3s  | 108.5s    |
| tol=576, step=48 | 5.1M       | 1.9M   | 226.0 | 206.0 | 39.2s  | 66.1s  | 109.9s    |
| tol=588, step=49 | 5.2M       | 1.9M   | 225.7 | 205.7 | 36.9s  | 65.0s  | 106.4s    |
| tol=600, step=50 | 5.4M       | 2.0M   | 224.0 | 204.3 | 37.1s  | 69.1s  | 111.0s    |

### Optimal Parameters: tolerance=336km, step=28s

## Running the Benchmark

Define parameters in `src/main/java/io/salad109/conjunctionapi/conjunction/internal/ConjunctionBenchmark.java`

```bash
# Run on Linux
./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-conjunction

# Run on Windows
./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-conjunction"
```