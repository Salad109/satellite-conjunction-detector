package io.salad109.conjunctiondetector.conjunction.internal;

import io.salad109.conjunctiondetector.satellite.Satellite;
import io.salad109.conjunctiondetector.satellite.SatellitePair;
import org.orekit.frames.Frame;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.IntStream;

@Service
public class ScanService {

    private final PropagationService propagationService;

    public ScanService(PropagationService propagationService) {
        this.propagationService = propagationService;
    }

    /**
     * Check for close approaches using spatial indexing only -- no pair pre-filter.
     */
    public List<CoarseDetection> checkPairs(Map<Integer, Satellite> satelliteById,
                                            PropagationService.PositionCache precomputedPositions,
                                            double toleranceKm, double cellSizeKm) {
        int totalSteps = precomputedPositions.times().length;
        double tolSq = toleranceKm * toleranceKm; // skip sqrt by comparing squared distances

        // Parallelize over time steps
        return IntStream.range(0, totalSteps)
                .parallel()
                .boxed()
                .<CoarseDetection>mapMulti((step, consumer) -> {
                    SpatialGrid grid = new SpatialGrid(cellSizeKm, precomputedPositions.x(), precomputedPositions.y(), precomputedPositions.z(), step);

                    grid.forEachCandidatePair((idxA, idxB) -> {
                        double distSq = precomputedPositions.distanceSquaredAt(idxA, idxB, step);
                        if (distSq < tolSq) {
                            int noradA = precomputedPositions.arrayIdToNoradId()[idxA];
                            int noradB = precomputedPositions.arrayIdToNoradId()[idxB];
                            Satellite satA = satelliteById.get(noradA);
                            Satellite satB = satelliteById.get(noradB);
                            SatellitePair pair = noradA < noradB
                                    ? new SatellitePair(satA, satB)
                                    : new SatellitePair(satB, satA);
                            consumer.accept(new CoarseDetection(pair, distSq, step));
                        }
                    });
                })
                .toList();
    }

    /**
     * Group detections by pair, then cluster consecutive detections into events (orbital passes).
     * Two detections belong to the same event if they're within 3 steps of each other.
     */
    public Map<SatellitePair, List<List<CoarseDetection>>> groupIntoEvents(List<CoarseDetection> detections) {
        List<CoarseDetection> sorted = detections.parallelStream()
                .sorted(Comparator
                        .comparingInt((CoarseDetection d) -> d.pair().a().getNoradCatId())
                        .thenComparingInt(d -> d.pair().b().getNoradCatId())
                        .thenComparingInt(CoarseDetection::stepIndex))
                .toList();

        Map<SatellitePair, List<List<CoarseDetection>>> result = new HashMap<>();
        if (sorted.isEmpty()) return result;

        SatellitePair currentPair = sorted.getFirst().pair();
        List<CoarseDetection> currentCluster = new ArrayList<>();
        List<List<CoarseDetection>> currentEvents = new ArrayList<>();
        currentCluster.add(sorted.getFirst());

        for (int i = 1; i < sorted.size(); i++) {
            CoarseDetection prev = sorted.get(i - 1);
            CoarseDetection curr = sorted.get(i);

            if (!curr.pair().equals(currentPair)) {
                currentEvents.add(currentCluster);
                result.put(currentPair, currentEvents);
                currentPair = curr.pair();
                currentEvents = new ArrayList<>();
                currentCluster = new ArrayList<>();
            } else if (curr.stepIndex() - prev.stepIndex() > 3) {
                currentEvents.add(currentCluster);
                currentCluster = new ArrayList<>();
            }
            currentCluster.add(curr);
        }
        currentEvents.add(currentCluster);
        result.put(currentPair, currentEvents);

        return result;
    }

    /**
     * Refine an event to find more accurate TCA and minimum distance.
     * Call SGP4 only for events that survive the threshold check.
     */
    public RefinedEvent refineEvent(List<CoarseDetection> event, PropagationService.PositionCache cache,
                                    Map<Integer, TLEPropagator> propagators, int stepSeconds, double thresholdKm) {
        CoarseDetection best = event.stream()
                .min(Comparator.comparing(CoarseDetection::distanceSq))
                .orElseThrow();
        SatellitePair pair = best.pair();
        int step = best.stepIndex();
        int totalSteps = cache.times().length;

        int idxA = cache.noradIdToArrayId().get(pair.a().getNoradCatId());
        int idxB = cache.noradIdToArrayId().get(pair.b().getNoradCatId());

        double thresholdSq = thresholdKm * thresholdKm;
        double bestDistSq = Double.MAX_VALUE;
        double bestT = 0;
        int bestIntervalStart = step;

        // Check interval (step-1, step)
        if (step > 0 && cache.isValid(idxA, step - 1) && cache.isValid(idxB, step - 1)
                && cache.isValid(idxA, step) && cache.isValid(idxB, step)) {
            double[] result = analyticalMin(cache, idxA, idxB, step - 1, step);
            if (result[0] < bestDistSq) {
                bestDistSq = result[0];
                bestT = result[1];
                bestIntervalStart = step - 1;
            }
        }

        // Check interval (step, step+1)
        if (step < totalSteps - 1 && cache.isValid(idxA, step) && cache.isValid(idxB, step)
                && cache.isValid(idxA, step + 1) && cache.isValid(idxB, step + 1)) {
            double[] result = analyticalMin(cache, idxA, idxB, step, step + 1);
            if (result[0] < bestDistSq) {
                bestDistSq = result[0];
                bestT = result[1];
                bestIntervalStart = step;
            }
        }

        // Early exit for events obviously above threshold
        if (bestDistSq > thresholdSq) {
            return null;
        }

        // Convert fractional t to absolute timestamp
        long intervalNanos = stepSeconds * 1_000_000_000L;
        OffsetDateTime tca = cache.times()[bestIntervalStart].plusNanos((long) (bestT * intervalNanos));

        PropagationService.MeasurementResult measurement = propagationService.propagateAndMeasure(pair, propagators, tca, thresholdKm);

        return new RefinedEvent(pair, measurement.distanceKm(), tca, measurement.velocityMS(),
                measurement.pvA(), measurement.pvB(), measurement.frame(), measurement.absoluteDate());
    }

    /**
     * With linear interpolation between two positions, squared distance is a quadratic in t.
     * Solve for the minimum analytically. Returns {minDistSq, t} where t in [0,1].
     */
    private double[] analyticalMin(PropagationService.PositionCache cache, int idxA, int idxB,
                                   int s0, int s1) {
        double sepX = cache.x()[idxA][s0] - cache.x()[idxB][s0];
        double sepY = cache.y()[idxA][s0] - cache.y()[idxB][s0];
        double sepZ = cache.z()[idxA][s0] - cache.z()[idxB][s0];
        double deltaSepX = (cache.x()[idxA][s1] - cache.x()[idxA][s0]) - (cache.x()[idxB][s1] - cache.x()[idxB][s0]);
        double deltaSepY = (cache.y()[idxA][s1] - cache.y()[idxA][s0]) - (cache.y()[idxB][s1] - cache.y()[idxB][s0]);
        double deltaSepZ = (cache.z()[idxA][s1] - cache.z()[idxA][s0]) - (cache.z()[idxB][s1] - cache.z()[idxB][s0]);

        double distSq0 = sepX * sepX + sepY * sepY + sepZ * sepZ;
        double sepDotDelta = sepX * deltaSepX + sepY * deltaSepY + sepZ * deltaSepZ;
        double deltaSepSq = deltaSepX * deltaSepX + deltaSepY * deltaSepY + deltaSepZ * deltaSepZ;

        double t = deltaSepSq == 0 ? 0.5 : Math.clamp(-sepDotDelta / deltaSepSq, 0, 1);
        return new double[]{distSq0 + (2 * sepDotDelta + deltaSepSq * t) * t, t};
    }


    public record CoarseDetection(SatellitePair pair, double distanceSq, int stepIndex) {
    }

    public record RefinedEvent(SatellitePair pair, double distanceKm, OffsetDateTime tca, double relativeVelocityMS,
                               PVCoordinates pvA, PVCoordinates pvB, Frame frame, AbsoluteDate absoluteDate) {
    }
}
