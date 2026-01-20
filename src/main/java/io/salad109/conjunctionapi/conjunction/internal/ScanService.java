package io.salad109.conjunctionapi.conjunction.internal;

import io.salad109.conjunctionapi.satellite.SatellitePair;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final PropagationService propagationService;

    public ScanService(PropagationService propagationService) {
        this.propagationService = propagationService;
    }

    public List<Conjunction> scanForConjunctions(List<SatellitePair> pairs, Map<Integer, TLEPropagator> propagators, double toleranceKm, double thresholdKm, int lookaheadHours, int stepSeconds, int interpolationStride) {
        log.debug("Starting conjunction scan for {} pairs over {} hours (tolerance={} km, threshold={} km, interpStride={})",
                pairs.size(), lookaheadHours, toleranceKm, thresholdKm, interpolationStride);
        // Coarse sweep
        List<CoarseDetection> coarseDetections = coarseSweep(pairs, propagators, OffsetDateTime.now(ZoneOffset.UTC), toleranceKm, stepSeconds, lookaheadHours, interpolationStride);
        log.info("Coarse sweep found {} detections", coarseDetections.size());

        if (coarseDetections.isEmpty()) {
            log.warn("No close approaches detected in lookahead window");
            return List.of();
        }

        // Group into events
        Map<SatellitePair, List<List<CoarseDetection>>> eventsByPair = groupIntoEvents(coarseDetections, stepSeconds);

        // Refine each event
        // Flatten for parallel processing
        List<List<CoarseDetection>> allEvents = eventsByPair.values().stream()
                .flatMap(List::stream)
                .toList();

        log.debug("Refining {} events...", allEvents.size());
        StopWatch refineWatch = StopWatch.createStarted();

        // Refine and filter by threshold
        List<Conjunction> conjunctionsUnderThreshold = allEvents.parallelStream()
                .map(event -> refineEvent(event, propagators, stepSeconds, thresholdKm))
                .filter(refined -> refined.getMissDistanceKm() <= thresholdKm)
                .toList();

        refineWatch.stop();
        log.info("Refined to {} conjunctions below {} km threshold in {}ms",
                conjunctionsUnderThreshold.size(), thresholdKm, refineWatch.getTime());

        // Deduplicate by pair, keeping the closest approach
        List<Conjunction> deduplicated = conjunctionsUnderThreshold.stream()
                .collect(Collectors.toMap(
                        c -> new IntPair(c.getObject1NoradId(), c.getObject2NoradId()),
                        c -> c,
                        (a, b) -> a.getMissDistanceKm() <= b.getMissDistanceKm() ? a : b
                ))
                .values()
                .stream()
                .toList();

        log.debug("Deduplicated to {} unique pairs", deduplicated.size());
        return deduplicated;
    }

    /**
     * Scan through lookahead window in large steps and record all detections within toleranceKm.
     */
    List<CoarseDetection> coarseSweep(List<SatellitePair> pairs, Map<Integer, TLEPropagator> propagators,
                                      OffsetDateTime startTime, double toleranceKm, int stepSeconds, int lookaheadHours,
                                      int interpolationStride) {
        int totalSteps = (lookaheadHours * 3600) / stepSeconds + 1;

        // Pre-compute all satellite positions
        PropagationService.PositionCache precomputedPositions = propagationService.precomputePositions(
                propagators, startTime, stepSeconds, totalSteps, interpolationStride);

        // Check all pairs
        List<CoarseDetection> detections = checkPairs(pairs, precomputedPositions, toleranceKm);

        return detections;
    }

    private List<CoarseDetection> checkPairs(List<SatellitePair> pairs, PropagationService.PositionCache precomputedPositions,
                                             double toleranceKm) {
        double tolSq = toleranceKm * toleranceKm; // skip sqrt by comparing squared distances
        int totalSteps = precomputedPositions.times().length;

        return pairs.parallelStream()
                .<CoarseDetection>mapMulti((pair, consumer) -> {
                    int idxA = precomputedPositions.noradIdToArrayId().get(pair.a().getNoradCatId());
                    int idxB = precomputedPositions.noradIdToArrayId().get(pair.b().getNoradCatId());

                    for (int step = 0; step < totalSteps; step++) {
                        if (!precomputedPositions.validAt(idxA, idxB, step)) continue;
                        double distSq = precomputedPositions.distanceSquaredAt(idxA, idxB, step, tolSq);
                        if (distSq < tolSq) {
                            consumer.accept(new CoarseDetection(pair, precomputedPositions.times()[step], Math.sqrt(distSq)));
                        }
                    }
                })
                .toList();
    }

    /**
     * Group detections by pair, then cluster consecutive detections into events (orbital passes).
     * Two detections belong to the same event if they're within 3 steps of each other.
     */
    Map<SatellitePair, List<List<CoarseDetection>>> groupIntoEvents(List<CoarseDetection> detections, int stepSeconds) {
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
    Conjunction refineEvent(List<CoarseDetection> event, Map<Integer, TLEPropagator> propagators, int stepSeconds, double thresholdKm) {
        CoarseDetection best = event.stream()
                .min(Comparator.comparing(CoarseDetection::distance))
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

        OffsetDateTime tca = startTime.plusNanos((long) result.getPoint());

        // Final accurate propagation at TCA
        PropagationService.DistanceAndVelocity result2 = propagationService.propagateAndMeasure(pair, propagators, tca, thresholdKm);
        double minDistance = result2.distanceKm();
        double relativeVelocity = result2.velocityKmS();

        // Ensure object 1 norad id < object 2 norad id
        int object1 = Math.min(pair.a().getNoradCatId(), pair.b().getNoradCatId());
        int object2 = Math.max(pair.a().getNoradCatId(), pair.b().getNoradCatId());

        return new Conjunction(
                null,
                object1,
                object2,
                minDistance,
                tca,
                relativeVelocity
        );
    }

    record CoarseDetection(SatellitePair pair, OffsetDateTime time, double distance) {
    }

    record IntPair(int a, int b) {
    }
}
