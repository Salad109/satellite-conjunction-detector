package io.salad109.conjunctiondetector.satellite.internal;

import io.salad109.conjunctiondetector.satellite.Satellite;
import io.salad109.conjunctiondetector.satellite.SatellitePair;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PairReductionNative {

    private static final StructLayout PAIR_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT
    );
    private final MethodHandle filterPairsHandle;

    public PairReductionNative() {
        Path libPath = resolveLibraryPath();
        System.load(libPath.toAbsolutePath().toString());
        SymbolLookup library = SymbolLookup.loaderLookup();

        FunctionDescriptor descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,           // return: pair count
                ValueLayout.JAVA_INT,           // n
                ValueLayout.ADDRESS,            // perigees
                ValueLayout.ADDRESS,            // apogees
                ValueLayout.ADDRESS,            // is_debris
                ValueLayout.ADDRESS,            // inclinations
                ValueLayout.ADDRESS,            // raans
                ValueLayout.ADDRESS,            // arg_perigees
                ValueLayout.ADDRESS,            // eccentricities
                ValueLayout.ADDRESS,            // semi_major_axes
                ValueLayout.JAVA_DOUBLE,        // tolerance_km
                ValueLayout.ADDRESS             // out_pairs
        );

        filterPairsHandle = Linker.nativeLinker().downcallHandle(
                library.find("filter_satellite_pairs").orElseThrow(() ->
                        new RuntimeException("'filter_satellite_pairs' not found in native library")),
                descriptor
        );
    }

    private static Path resolveLibraryPath() {
        Path dev = Path.of("src/main/c/pair_reduction.so");
        if (Files.exists(dev)) return dev;

        Path docker = Path.of("/app/lib/pair_reduction.so");
        if (Files.exists(docker)) return docker;

        throw new RuntimeException("Native library not found");
    }

    public List<SatellitePair> findPairs(List<Satellite> satellites, double toleranceKm) {
        int n = satellites.size();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment perigees = arena.allocate(ValueLayout.JAVA_DOUBLE, n);
            MemorySegment apogees = arena.allocate(ValueLayout.JAVA_DOUBLE, n);
            MemorySegment isDebris = arena.allocate(ValueLayout.JAVA_INT, n);
            MemorySegment inclinations = arena.allocate(ValueLayout.JAVA_DOUBLE, n);
            MemorySegment raans = arena.allocate(ValueLayout.JAVA_DOUBLE, n);
            MemorySegment argPerigees = arena.allocate(ValueLayout.JAVA_DOUBLE, n);
            MemorySegment eccentricities = arena.allocate(ValueLayout.JAVA_DOUBLE, n);
            MemorySegment semiMajorAxes = arena.allocate(ValueLayout.JAVA_DOUBLE, n);

            for (int i = 0; i < n; i++) {
                Satellite sat = satellites.get(i);
                perigees.setAtIndex(ValueLayout.JAVA_DOUBLE, i, sat.getPerigeeKm());
                apogees.setAtIndex(ValueLayout.JAVA_DOUBLE, i, sat.getApogeeKm());
                isDebris.setAtIndex(ValueLayout.JAVA_INT, i, "DEBRIS".equals(sat.getObjectType()) ? 1 : 0);
                inclinations.setAtIndex(ValueLayout.JAVA_DOUBLE, i, sat.getInclination());
                raans.setAtIndex(ValueLayout.JAVA_DOUBLE, i, sat.getRaan());
                argPerigees.setAtIndex(ValueLayout.JAVA_DOUBLE, i, sat.getArgPerigee());
                eccentricities.setAtIndex(ValueLayout.JAVA_DOUBLE, i, sat.getEccentricity());
                semiMajorAxes.setAtIndex(ValueLayout.JAVA_DOUBLE, i, sat.getSemiMajorAxisKm());
            }

            long maxPairs = (long) n * (n - 1) / 2 / 4; // 25% of theoretical max
            MemorySegment outPairs = arena.allocate(PAIR_LAYOUT, maxPairs);

            int pairCount = (int) filterPairsHandle.invoke(
                    n,
                    perigees,
                    apogees,
                    isDebris,
                    inclinations,
                    raans,
                    argPerigees,
                    eccentricities,
                    semiMajorAxes,
                    toleranceKm,
                    outPairs
            );

            List<SatellitePair> results = new ArrayList<>(pairCount);
            long pairSize = PAIR_LAYOUT.byteSize();

            for (int i = 0; i < pairCount; i++) {
                long offset = i * pairSize;
                int idxA = outPairs.get(ValueLayout.JAVA_INT, offset);
                int idxB = outPairs.get(ValueLayout.JAVA_INT, offset + 4);
                results.add(new SatellitePair(satellites.get(idxA), satellites.get(idxB)));
            }

            return results;

        } catch (Throwable e) {
            throw new RuntimeException("Native pair reduction failed", e);
        }
    }
}
