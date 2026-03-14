package io.salad109.conjunctiondetector.conjunction.internal;

import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

import java.util.ArrayList;

public class SpatialGrid {

    // 13 positive neighbor offsets (half of 26 neighbors to avoid duplicate pairs)
    private static final int[][] HALF_NEIGHBOR_OFFSETS = {
            {1, 0, 0},    // +x
            {0, 1, 0},    // +y
            {0, 0, 1},    // +z
            {1, 1, 0},    // +x+y
            {1, 0, 1},    // +x+z
            {0, 1, 1},    // +y+z
            {1, 1, 1},    // +x+y+z
            {1, -1, 0},   // +x-y
            {1, 0, -1},   // +x-z
            {0, 1, -1},   // +y-z
            {1, 1, -1},   // +x+y-z
            {1, -1, 1},   // +x-y+z
            {1, -1, -1},  // +x-y-z
    };

    // Reuse to reduce hot path allocations
    private static final ThreadLocal<IntObjectHashMap<IntArrayList>> MAP_POOL
            = ThreadLocal.withInitial(() -> new IntObjectHashMap<>(512));
    private static final ThreadLocal<ArrayList<IntArrayList>> BUCKET_POOL =
            ThreadLocal.withInitial(ArrayList::new);

    private final double cellSizeKm;
    private final IntObjectHashMap<IntArrayList> grid;

    public SpatialGrid(double cellSizeKm, float[][] x, float[][] y, float[][] z, int step) {
        this.cellSizeKm = cellSizeKm;

        IntObjectHashMap<IntArrayList> map = MAP_POOL.get();
        ArrayList<IntArrayList> bucketPool = BUCKET_POOL.get();

        map.forEachValue(bucket -> {
            bucket.clear();
            bucketPool.add(bucket);
        });
        map.clear();

        int numSatellites = x.length;
        for (int satIdx = 0; satIdx < numSatellites; satIdx++) {
            if (Float.isNaN(x[satIdx][step])) continue;

            int cellKey = cellHash(x[satIdx][step], y[satIdx][step], z[satIdx][step]);
            IntArrayList bucket = map.get(cellKey);
            if (bucket == null) {
                bucket = bucketPool.isEmpty() ? new IntArrayList(4) : bucketPool.removeLast();
                map.put(cellKey, bucket);
            }
            bucket.add(satIdx);
        }

        this.grid = map;
    }

    /**
     * Pack three 10-bit signed cell coordinates into a 32-bit key: [unused:2][x:10][y:10][z:10]
     */
    private static int packCellKey(int cx, int cy, int cz) {
        // Mask to 10 bits (mod 1024)
        int mx = cx & 0x3FF;
        int my = cy & 0x3FF;
        int mz = cz & 0x3FF;
        return (mx << 20) | (my << 10) | mz;
    }

    /**
     * Iterate all candidate pairs that could be within tolerance.
     */
    public void forEachCandidatePair(IntBiConsumer consumer) {
        grid.forEachKeyValue((cellKey, satellites) -> {
            int size = satellites.size();

            // Same-cell pairs
            for (int i = 0; i < size; i++) {
                int idxA = satellites.get(i);
                for (int j = i + 1; j < size; j++) {
                    int idxB = satellites.get(j);
                    consumer.accept(idxA, idxB);
                }
            }

            // Adjacent-cell pairs (13 "half" neighbors)
            int cx = (cellKey >> 20) & 0x3FF;
            int cy = (cellKey >> 10) & 0x3FF;
            int cz = cellKey & 0x3FF;

            for (int[] offset : HALF_NEIGHBOR_OFFSETS) {
                int neighborKey = packCellKey(cx + offset[0], cy + offset[1], cz + offset[2]);
                IntArrayList neighborSatellites = grid.get(neighborKey);
                if (neighborSatellites == null) continue;

                int neighborSize = neighborSatellites.size();
                for (int i = 0; i < size; i++) {
                    int idxA = satellites.get(i);
                    for (int j = 0; j < neighborSize; j++) {
                        int idxB = neighborSatellites.get(j);
                        consumer.accept(idxA, idxB);
                    }
                }
            }
        });
    }

    /**
     * Hash a 3D position to a cell key.
     * Cell indices are 10-bit signed integers, packed into a 32-bit int.
     */
    private int cellHash(double px, double py, double pz) {
        int cx = (int) Math.floor(px / cellSizeKm);
        int cy = (int) Math.floor(py / cellSizeKm);
        int cz = (int) Math.floor(pz / cellSizeKm);
        return packCellKey(cx, cy, cz);
    }

    @FunctionalInterface
    public interface IntBiConsumer {
        void accept(int a, int b);
    }
}
