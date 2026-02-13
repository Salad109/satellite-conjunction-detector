package io.salad109.conjunctiondetector.conjunction.internal;

import io.salad109.conjunctiondetector.satellite.PairReductionService;
import io.salad109.conjunctiondetector.satellite.Satellite;
import io.salad109.conjunctiondetector.satellite.SatellitePair;
import io.salad109.conjunctiondetector.satellite.SatelliteService;
import org.apache.commons.lang3.time.StopWatch;
import org.jspecify.annotations.NonNull;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Linux:
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-conjunction -Dspring-boot.run.jvmArguments="-Xmx16g -Xms16g -XX:+AlwaysPreTouch --enable-native-access=ALL-UNNAMED"
 * Windows:
 * ./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-conjunction" "-Dspring-boot.run.jvmArguments=-Xmx16g -Xms16g -XX:+AlwaysPreTouch --enable-native-access=ALL-UNNAMED"
 */
@Component
@Profile("benchmark-conjunction")
public class ConjunctionBenchmark implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConjunctionBenchmark.class);

    private final SatelliteService satelliteService;
    private final PairReductionService pairReductionService;
    private final PropagationService propagationService;
    private final ScanService scanService;
    private final CollisionProbabilityService collisionProbabilityService;

    public ConjunctionBenchmark(SatelliteService satelliteService, PairReductionService pairReductionService, PropagationService propagationService, ScanService scanService, CollisionProbabilityService collisionProbabilityService) {
        this.satelliteService = satelliteService;
        this.pairReductionService = pairReductionService;
        this.propagationService = propagationService;
        this.scanService = scanService;
        this.collisionProbabilityService = collisionProbabilityService;
    }

    @Override
    public void run(String @NonNull ... args) throws InterruptedException {
        log.info("");
        log.info("Starting conjunction benchmark");
        log.info("");

        List<Satellite> satellites = satelliteService.getAll();
        log.info("Loaded {} satellites", satellites.size());

        // Use fixed start time
        OffsetDateTime fixedStartTime = OffsetDateTime.of(2026, 1, 13, 19, 0, 0, 0, ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS);
        log.info("Using fixed start time: {}", fixedStartTime);


        // Benchmark parameters
        int lookaheadHours = 24;
        double thresholdKm = 5.0;
        double prepassToleranceKm = 10.0;
        int stepSecondRatio = 10;
        int interpolationStride = 6;

        List<BenchmarkResult> results = new ArrayList<>();

        //noinspection InfiniteLoopStatement
        while (true) {
            for (double toleranceKm = 50; toleranceKm <= 400; toleranceKm += stepSecondRatio) {
                int stepSeconds = (int) (toleranceKm / stepSecondRatio);

                System.gc();
                //noinspection BusyWait
                Thread.sleep(100);
                results.add(runBenchmark(satellites, fixedStartTime,
                        toleranceKm, prepassToleranceKm, stepSeconds, stepSecondRatio,
                        lookaheadHours, thresholdKm, interpolationStride));
            }

            writeCsvResults(results);
            results.clear();
        }
    }

    private BenchmarkResult runBenchmark(
            List<Satellite> satellites,
            OffsetDateTime startTime,
            double toleranceKm,
            double prepassToleranceKm,
            int stepSeconds,
            int stepSecondRatio,
            int lookaheadHours,
            double thresholdKm,
            int interpolationStride
    ) {
        StopWatch total = StopWatch.createStarted();

        // Pair reduction
        StopWatch pairReduction = StopWatch.createStarted();
        List<SatellitePair> pairs = pairReductionService.findPotentialCollisionPairs(satellites, prepassToleranceKm);
        pairReduction.stop();

        // Filter to only candidate satellites
        StopWatch filter = StopWatch.createStarted();
        List<Satellite> candidateSatellites = SatellitePair.uniqueSatellites(pairs, satellites);
        filter.stop();

        // Build propagators
        StopWatch propagator = StopWatch.createStarted();
        Map<Integer, TLEPropagator> propagators = propagationService.buildPropagators(candidateSatellites);
        propagator.stop();

        // Propagate satellites
        StopWatch propagateSweep = StopWatch.createStarted();
        PropagationService.PositionCache positionCache = propagationService.precomputePositions(
                propagators, startTime, stepSeconds, lookaheadHours, interpolationStride);
        propagateSweep.stop();

        // Check pairs against positions
        StopWatch checkPairs = StopWatch.createStarted();
        List<ScanService.CoarseDetection> detections = scanService.checkPairs(pairs, positionCache, toleranceKm);
        checkPairs.stop();

        // Group into events
        StopWatch grouping = StopWatch.createStarted();
        Map<SatellitePair, List<List<ScanService.CoarseDetection>>> eventsByPair = scanService.groupIntoEvents(detections, stepSeconds);
        int totalEvents = eventsByPair.values().stream().mapToInt(List::size).sum();
        List<List<ScanService.CoarseDetection>> allEvents = eventsByPair.values().stream()
                .flatMap(List::stream)
                .toList();
        grouping.stop();

        // Refinement
        StopWatch refine = StopWatch.createStarted();
        List<ScanService.RefinedEvent> refined = allEvents.parallelStream().map(event -> scanService.refineEvent(event, propagators, stepSeconds, thresholdKm))
                .filter(Objects::nonNull)
                .toList();
        refine.stop();

        // Collision probability
        StopWatch probability = StopWatch.createStarted();
        List<Conjunction> conjunctions = refined.parallelStream()
                .map(collisionProbabilityService::computeProbabilityAndBuild)
                .toList();
        probability.stop();

        total.stop();

        String name = String.format("tol=%.0f, prepass=%.1f, step=%d, stride=%d", toleranceKm, prepassToleranceKm, stepSeconds, interpolationStride);
        log.info("tol={}km | {}ms | pair={}ms filter={}ms prop={}ms propagate={}ms check={}ms group={}ms refine={}ms pc={}ms | {} conj",
                (int) toleranceKm, total.getTime(), pairReduction.getTime(), filter.getTime(), propagator.getTime(),
                propagateSweep.getTime(), checkPairs.getTime(), grouping.getTime(), refine.getTime(), probability.getTime(),
                conjunctions.size());

        return new BenchmarkResult(name, toleranceKm, prepassToleranceKm, stepSeconds, stepSecondRatio,
                interpolationStride, detections.size(), totalEvents, conjunctions.size(),
                pairReduction.getTime(), filter.getTime(), propagator.getTime(), propagateSweep.getTime(),
                checkPairs.getTime(), grouping.getTime(), refine.getTime(), probability.getTime(), total.getTime());
    }

    private void writeCsvResults(List<BenchmarkResult> results) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "conjunction_benchmark_" + timestamp + ".csv";
        Path outputPath = Paths.get("docs", filename);

        try {
            try (FileWriter writer = new FileWriter(outputPath.toFile())) {
                writer.write("tolerance_km,prepass_km,step_s,step_ratio,interp_stride,detections,events,conj,pair_reduction_s,filter_s,propagator_s,propagate_s,check_s,grouping_s,refine_s,probability_s,total_s\n");

                for (BenchmarkResult r : results) {
                    writer.write(String.format("%.0f,%.1f,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                            r.toleranceKm,
                            r.prepassToleranceKm,
                            r.stepSeconds,
                            r.stepSecondRatio,
                            r.interpolationStride,
                            r.detections,
                            r.events,
                            r.conjunctions,
                            r.pairReductionTime / 1000.0,
                            r.filterTime / 1000.0,
                            r.propagatorTime / 1000.0,
                            r.propagateSweepTimeMs / 1000.0,
                            r.checkPairsTimeMs / 1000.0,
                            r.groupingTime / 1000.0,
                            r.refineTimeMs / 1000.0,
                            r.probabilityTimeMs / 1000.0,
                            r.totalTimeMs / 1000.0));
                }
            }

            log.info("CSV results written to: {}", outputPath.toAbsolutePath());

        } catch (IOException e) {
            log.error("Failed to write CSV file", e);
        }
    }

    private record BenchmarkResult(String name, double toleranceKm, double prepassToleranceKm, int stepSeconds,
                                   int stepSecondRatio, int interpolationStride, long detections, int events,
                                   int conjunctions,
                                   long pairReductionTime, long filterTime,
                                   long propagatorTime, long propagateSweepTimeMs, long checkPairsTimeMs,
                                   long groupingTime, long refineTimeMs, long probabilityTimeMs, long totalTimeMs) {
    }
}
