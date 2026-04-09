package io.salad109.conjunctiondetector.conjunction.internal;

import io.salad109.conjunctiondetector.conjunction.internal.PropagationService.PositionCache;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class AnalyticalMinTest {

    private final ScanService scanService = new ScanService(null);

    @Test
    void minimumAtMidpointForHeadOnApproach() {
        // A: (0,0,0)->(10,0,0), B: (10,0,0)->(0,0,0)
        PositionCache cache = buildCache(
                new float[][]{{0, 10}, {10, 0}},
                new float[][]{{0, 0}, {0, 0}},
                new float[][]{{0, 0}, {0, 0}}
        );

        double[] result = scanService.analyticalMin(cache, 0, 1, 0, 1);

        assertThat(result[0]).isCloseTo(0.0, offset(1e-6));
        assertThat(result[1]).isCloseTo(0.5, offset(1e-6));
    }

    @Test
    void minimumClampedToStartWhenDiverging() {
        // A: (0,0,0)->(100,0,0), B: (0,0,0)->(-100,0,0)
        PositionCache cache = buildCache(
                new float[][]{{0, 100}, {0, -100}},
                new float[][]{{0, 0}, {0, 0}},
                new float[][]{{0, 0}, {0, 0}}
        );

        double[] result = scanService.analyticalMin(cache, 0, 1, 0, 1);

        assertThat(result[0]).isCloseTo(0.0, offset(1e-6));
        assertThat(result[1]).isCloseTo(0.0, offset(1e-6));
    }

    @Test
    void minimumClampedToEndWhenConverging() {
        // A: (-100,0,0)->(0,0,0), B: (100,0,0)->(1,0,0)
        PositionCache cache = buildCache(
                new float[][]{{-100, 0}, {100, 1}},
                new float[][]{{0, 0}, {0, 0}},
                new float[][]{{0, 0}, {0, 0}}
        );

        double[] result = scanService.analyticalMin(cache, 0, 1, 0, 1);

        assertThat(result[0]).isCloseTo(1.0, offset(1e-6));
        assertThat(result[1]).isCloseTo(1.0, offset(1e-6));
    }

    @Test
    void minimumDistance3D() {
        // Perpendicular pass with 3 km z-offset. Min at t=0.5, distSq=9
        PositionCache cache = buildCache(
                new float[][]{{0, 10}, {5, 5}},
                new float[][]{{5, 5}, {0, 10}},
                new float[][]{{0, 0}, {3, 3}}
        );

        double[] result = scanService.analyticalMin(cache, 0, 1, 0, 1);

        assertThat(result[0]).isCloseTo(9.0, offset(1e-6));
        assertThat(result[1]).isCloseTo(0.5, offset(1e-6));
    }

    @Test
    void nearlyParallelTrajectoriesNoNaNOrInfinity() {
        // Same x velocity, tiny y crossing
        PositionCache cache = buildCache(
                new float[][]{{0.0f, 1000.0f}, {0.0f, 1000.0f}},
                new float[][]{{0.0f, 0.0f}, {-0.001f, 0.0005f}},
                new float[][]{{0.0f, 0.0f}, {0.0f, 0.0f}}
        );

        double[] result = scanService.analyticalMin(cache, 0, 1, 0, 1);

        assertThat(result[0]).as("distSq").isCloseTo(0.0, offset(1e-6));
        assertThat(result[1]).as("t").isCloseTo(2.0 / 3.0, offset(1e-6));
    }

    @Test
    void exactlyParallelTrajectoriesFallBackToHalf() {
        // Identical velocity vectors, constant 5 km separation (3^2+4^2=25)
        PositionCache cache = buildCache(
                new float[][]{{0.0f, 100.0f}, {3.0f, 103.0f}},
                new float[][]{{0.0f, 0.0f}, {0.0f, 0.0f}},
                new float[][]{{0.0f, 0.0f}, {4.0f, 4.0f}}
        );

        double[] result = scanService.analyticalMin(cache, 0, 1, 0, 1);

        assertThat(result[1]).isCloseTo(0.5, offset(1e-6));
        assertThat(result[0]).isCloseTo(25.0, offset(1e-6));
    }

    @Test
    void distSqNonNegativeForAll3DCombinations() {
        float[][] sets = {
                {-7000f, 0f, 7000f},
                {-7000f, -1f, 7000f},
                {-100f, 0f, 100f},
                {-1f, 0f, 1f},
                {-7000f, -100f, 0f},
                {0f, 100f, 7000f},
        };
        for (float[] v : sets) {
            for (float ax0 : v)
                for (float ax1 : v)
                    for (float bx0 : v)
                        for (float bx1 : v)
                            for (float ay0 : v)
                                for (float ay1 : v)
                                    for (float by0 : v)
                                        for (float by1 : v)
                                            for (float az0 : v)
                                                for (float az1 : v)
                                                    for (float bz0 : v)
                                                        for (float bz1 : v) {
                                                            PositionCache cache = buildCache(
                                                                    new float[][]{{ax0, ax1}, {bx0, bx1}},
                                                                    new float[][]{{ay0, ay1}, {by0, by1}},
                                                                    new float[][]{{az0, az1}, {bz0, bz1}}
                                                            );
                                                            double[] result = scanService.analyticalMin(cache, 0, 1, 0, 1);
                                                            assertThat(result[0]).isGreaterThanOrEqualTo(0.0);
                                                            assertThat(result[1]).isBetween(0.0, 1.0);
                                                        }
        }
    }

    private PositionCache buildCache(float[][] x, float[][] y, float[][] z) {
        IntIntHashMap idMap = new IntIntHashMap();
        idMap.put(1, 0);
        idMap.put(2, 1);
        OffsetDateTime[] times = {OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(10)};
        return new PositionCache(idMap, new int[]{1, 2}, times, x, y, z);
    }
}
