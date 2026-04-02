# Subwindow Count

The PositionCache is `numSats * totalSteps * 3 floats * 4 bytes`. At 30k satellites and step-seconds=9, a 24h window
is 3.5 GB. A 7-day window is 24.2 GB. This is too large for most systems.

`subwindow-count` splits the lookahead window into N sequential chunks. Each chunk runs the cache-dependent stages
(propagate, interpolate, coarse scan, group, refine) and the cache goes out of scope before the next chunk starts.
Collision probability and persistence run once after all chunks finish. Peak cache memory is roughly `1/N` of the
single-window case.

`subwindow-count=1` effectively disables subwindowing.

## Cache size estimates

Rough PositionCache size per subwindow for 30k satellites at step-seconds=9:

| Window   | Count | Steps/Sub | Cache/Sub |
|----------|-------|-----------|-----------|
| 24 hours | 1     | 9,601     | 3.5 GB    |
| 24 hours | 4     | 2,401     | 0.9 GB    |
| 24 hours | 8     | 1,201     | 0.4 GB    |
| 7 days   | 7     | 9,601     | 3.5 GB    |
| 7 days   | 14    | 4,801     | 1.7 GB    |
| 7 days   | 28    | 2,401     | 0.9 GB    |

These are float array sizes only. Actual heap is slightly higher (intermediate collections, Spring Boot and JVM). At
very high counts the constant overhead dominates and cache savings become negligible in practice.

## Boundary duplicates

A conjunction straddling a subwindow boundary can appear in both subwindows. Testing with count=4 on a 24h window
produced 576 duplicates out of ~51k conjunctions (1.1%). The system allows multiple events per pair, so these are
harmless.

## Recommended values

For 24h lookahead window, use 4. For 7 days, use 28 (same cache size per subwindow as 24h/4).

Higher counts cause no meaningful speed penalty, and may arguably improve performance in memory-constrained environments
by reducing GC pressure.
