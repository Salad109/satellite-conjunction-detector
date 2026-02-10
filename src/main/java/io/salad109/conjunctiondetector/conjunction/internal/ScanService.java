package io.salad109.conjunctiondetector.conjunction.internal;

import io.salad109.conjunctiondetector.satellite.SatellitePair;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.orekit.frames.Frame;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ScanService {

    private final PropagationService propagationService;

    public ScanService(PropagationService propagationService) {
        this.propagationService = propagationService;
    }

    /**
     * Check for close approaches using spatial indexing.
     */
    public List<CoarseDetection> checkPairs(List<SatellitePair> pairs, PropagationService.PositionCache precomputedPositions,
                                            double toleranceKm) {
        // Build lookup for fast access in hot loop
        LongObjectHashMap<SatellitePair> allowedPairs = new LongObjectHashMap<>(pairs.size());
        for (SatellitePair pair : pairs) {
            int idxA = precomputedPositions.noradIdToArrayId().get(pair.a().getNoradCatId());
            int idxB = precomputedPositions.noradIdToArrayId().get(pair.b().getNoradCatId());
            long key = idxA < idxB ? ((long) idxA << 32) | idxB : ((long) idxB << 32) | idxA;
            allowedPairs.put(key, pair);
        }

        int totalSteps = precomputedPositions.times().length;
        double tolSq = toleranceKm * toleranceKm; // skip sqrt by comparing squared distances

        // Parallelize over time steps
        return IntStream.range(0, totalSteps)
                .parallel()
                .boxed()
                .<CoarseDetection>mapMulti((step, consumer) -> {
                    SpatialGrid grid = new SpatialGrid(toleranceKm, precomputedPositions.x(), precomputedPositions.y(), precomputedPositions.z(), precomputedPositions.valid(), step);

                    grid.forEachCandidatePair((idxA, idxB) -> {
                        // Filter by pair reduction
                        long key = idxA < idxB ? ((long) idxA << 32) | idxB : ((long) idxB << 32) | idxA;
                        SatellitePair pair = allowedPairs.get(key);
                        if (pair == null) return;

                        double distSq = precomputedPositions.distanceSquaredAt(idxA, idxB, step);
                        if (distSq < tolSq) {
                            consumer.accept(new CoarseDetection(pair, precomputedPositions.times()[step], distSq));
                        }
                    });
                })
                .toList();
    }

    /**
     * Group detections by pair, then cluster consecutive detections into events (orbital passes).
     * Two detections belong to the same event if they're within 3 steps of each other.
     */
    public Map<SatellitePair, List<List<CoarseDetection>>> groupIntoEvents(List<CoarseDetection> detections, int stepSeconds) {
        // Group by pair
        Map<SatellitePair, List<CoarseDetection>> byPair = detections.stream()
                .collect(Collectors.groupingBy(CoarseDetection::pair));

        // Cluster each pair's detections by time gap
        int gapThresholdSeconds = stepSeconds * 3;

        return byPair.entrySet().parallelStream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            List<CoarseDetection> sorted = entry.getValue().stream()
                                    .sorted(Comparator.comparing(CoarseDetection::time))
                                    .toList();
                            return clusterByTimeGap(sorted, gapThresholdSeconds);
                        }
                ));
    }

    /**
     * Cluster sorted detections into groups where consecutive items are within gapThresholdSeconds.
     */
    private List<List<CoarseDetection>> clusterByTimeGap(List<CoarseDetection> sorted, int gapThresholdSeconds) {
        List<List<CoarseDetection>> clusters = new ArrayList<>();
        if (sorted.isEmpty()) return clusters;

        List<CoarseDetection> currentCluster = new ArrayList<>();
        currentCluster.add(sorted.getFirst());

        for (int i = 1; i < sorted.size(); i++) {
            CoarseDetection prev = sorted.get(i - 1);
            CoarseDetection curr = sorted.get(i);

            long gapSeconds = ChronoUnit.SECONDS.between(prev.time(), curr.time());

            if (gapSeconds <= gapThresholdSeconds) {
                currentCluster.add(curr);
            } else {
                clusters.add(currentCluster);
                currentCluster = new ArrayList<>();
                currentCluster.add(curr);
            }
        }
        clusters.add(currentCluster);

        return clusters;
    }

    /**
     * Refine an event (cluster of coarse detections) using Brent's method to find more accurate TCA and minimum distance.
     * Uses linear interpolation during optimization to avoid expensive SGP4 calls, then does one final propagation
     * at the found TCA for accurate distance measurement.
     */
    public RefinedEvent refineEvent(List<CoarseDetection> event, Map<Integer, TLEPropagator> propagators, int stepSeconds, double thresholdKm) {
        CoarseDetection best = event.stream()
                .min(Comparator.comparing(CoarseDetection::distanceSq))
                .orElseThrow();
        SatellitePair pair = best.pair();

        // Search interval is stepSeconds/2 on each side of best detection
        long halfWindowNanos = (stepSeconds * 1_000_000_000L) / 2;
        long windowNanos = 2 * halfWindowNanos;
        OffsetDateTime startTime = best.time().minusNanos(halfWindowNanos);
        OffsetDateTime endTime = best.time().plusNanos(halfWindowNanos);

        // Pre-compute positions at window endpoints (4 SGP4 calls total)
        double[] startA = propagationService.propagateToPositionKm(pair.a(), propagators, startTime);
        double[] endA = propagationService.propagateToPositionKm(pair.a(), propagators, endTime);
        double[] startB = propagationService.propagateToPositionKm(pair.b(), propagators, startTime);
        double[] endB = propagationService.propagateToPositionKm(pair.b(), propagators, endTime);

        // Use Brent's method from Apache Commons Math
        // Only absolute tolerance matters. 0.017s = 0.25km@15km/s worst-case scenario is sufficient precision
        BrentOptimizer optimizer = new BrentOptimizer(1e-8, 16_666_667);

        // Minimize distance squared - same minimum location as with sqrt
        UnivariateObjectiveFunction objectiveFunction = new UnivariateObjectiveFunction(offsetNanos -> {
            double t = offsetNanos / windowNanos; // 0 to 1
            // Linear interpolation for both satellites
            double ax = startA[0] + t * (endA[0] - startA[0]);
            double ay = startA[1] + t * (endA[1] - startA[1]);
            double az = startA[2] + t * (endA[2] - startA[2]);
            double bx = startB[0] + t * (endB[0] - startB[0]);
            double by = startB[1] + t * (endB[1] - startB[1]);
            double bz = startB[2] + t * (endB[2] - startB[2]);

            double dx = ax - bx;
            double dy = ay - by;
            double dz = az - bz;

            return dx * dx + dy * dy + dz * dz;
        });

        UnivariatePointValuePair result = optimizer.optimize(
                objectiveFunction,
                GoalType.MINIMIZE,
                new SearchInterval(0, windowNanos),
                MaxEval.unlimited()
        );

        // Check if interpolated minimum distance is under threshold (compare squared to avoid sqrt)
        double thresholdSq = thresholdKm * thresholdKm;
        if (result.getValue() > thresholdSq) {
            return null; // Skip expensive propagation for events that won't meet threshold
        }

        OffsetDateTime tca = startTime.plusNanos((long) result.getPoint());

        // Final accurate propagation at TCA (only for events likely under threshold)
        PropagationService.MeasurementResult measurement = propagationService.propagateAndMeasure(pair, propagators, tca, thresholdKm);

        return new RefinedEvent(pair, measurement.distanceKm(), tca, measurement.velocityKmS(),
                measurement.pvA(), measurement.pvB(), measurement.frame(), measurement.absoluteDate());
    }

    public record CoarseDetection(SatellitePair pair, OffsetDateTime time, double distanceSq) {
    }

    public record RefinedEvent(SatellitePair pair, double distanceKm, OffsetDateTime tca, double relativeVelocityKmS,
                               PVCoordinates pvA, PVCoordinates pvB, Frame frame, AbsoluteDate absoluteDate) {
    }
}
