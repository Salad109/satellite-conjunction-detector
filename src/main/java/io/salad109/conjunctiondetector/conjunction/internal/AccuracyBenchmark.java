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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Linux:
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-accuracy -Dspring-boot.run.jvmArguments="-Xmx12g -Xms12g -XX:+AlwaysPreTouch -XX:+UseShenandoahGC --enable-native-access=ALL-UNNAMED"
 * Windows:
 * ./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-accuracy" "-Dspring-boot.run.jvmArguments=-Xmx12g -Xms12g -XX:+AlwaysPreTouch -XX:+UseShenandoahGC --enable-native-access=ALL-UNNAMED"
 */
@Component
@Profile("benchmark-accuracy")
public class AccuracyBenchmark implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AccuracyBenchmark.class);

    private static final int ITERATIONS = 3;

    private static final int LOOKAHEAD_HOURS = 24;
    private static final double THRESHOLD_KM = 5.0;
    private static final double TOLERANCE_KM = 250.0;

    // Defaults used when not being swept
    private static final double DEFAULT_PREPASS_KM = 30.0;
    private static final int DEFAULT_STEP_RATIO = 10;
    private static final int DEFAULT_STRIDE = 8;

    // Sweep values
    private static final double[] PREPASS_VALUES = {5, 10, 15, 20, 25, 30, 40, 50};
    private static final int[] STEP_RATIO_VALUES = {6, 7, 8, 9, 10, 15};
    private static final int[] STRIDE_VALUES = {1, 4, 8, 12, 16, 24, 32, 48, 64};

    private final SatelliteService satelliteService;
    private final PairReductionService pairReductionService;
    private final PropagationService propagationService;
    private final ScanService scanService;
    private final CollisionProbabilityService collisionProbabilityService;

    public AccuracyBenchmark(SatelliteService satelliteService, PairReductionService pairReductionService,
                             PropagationService propagationService, ScanService scanService,
                             CollisionProbabilityService collisionProbabilityService) {
        this.satelliteService = satelliteService;
        this.pairReductionService = pairReductionService;
        this.propagationService = propagationService;
        this.scanService = scanService;
        this.collisionProbabilityService = collisionProbabilityService;
    }

    @Override
    public void run(String @NonNull ... args) throws InterruptedException {
        log.info("");
        log.info("Starting conjunction accuracy benchmark");
        log.info("");

        List<Satellite> satellites = satelliteService.getAll();
        log.info("Loaded {} satellites", satellites.size());

        OffsetDateTime fixedStartTime = OffsetDateTime.of(2026, 2, 15, 19, 35, 0, 0, ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS);
        log.info("Using fixed start time: {}", fixedStartTime);
        log.info("Fixed tolerance: {} km, threshold: {} km, lookahead: {} h",
                TOLERANCE_KM, THRESHOLD_KM, LOOKAHEAD_HOURS);

        log.info("");
        log.info("Sweeping prepass tolerance");
        log.info("Locked: ratio={}, stride={}", DEFAULT_STEP_RATIO, DEFAULT_STRIDE);
        {
            List<BenchmarkResult> results = new ArrayList<>();
            int stepSeconds = (int) (TOLERANCE_KM / DEFAULT_STEP_RATIO);
            for (double prepass : PREPASS_VALUES) {
                results.addAll(runIterations(satellites, fixedStartTime, prepass,
                        stepSeconds, DEFAULT_STEP_RATIO, DEFAULT_STRIDE));
            }
            writeCsv(results, Paths.get("docs", "2-conjunction-prepass", "conjunction_benchmark.csv"));
        }

        log.info("");
        log.info("Sweeping step ratio");
        log.info("Locked: prepass={}, stride={}", DEFAULT_PREPASS_KM, DEFAULT_STRIDE);
        {
            List<BenchmarkResult> results = new ArrayList<>();
            for (int ratio : STEP_RATIO_VALUES) {
                int stepSeconds = (int) (TOLERANCE_KM / ratio);
                results.addAll(runIterations(satellites, fixedStartTime, DEFAULT_PREPASS_KM,
                        stepSeconds, ratio, DEFAULT_STRIDE));
            }
            writeCsv(results, Paths.get("docs", "3-conjunction-step-ratio", "conjunction_benchmark.csv"));
        }

        log.info("");
        log.info("Sweeping interpolation stride");
        log.info("Locked: prepass={}, ratio={}", DEFAULT_PREPASS_KM, DEFAULT_STEP_RATIO);
        {
            List<BenchmarkResult> results = new ArrayList<>();
            int stepSeconds = (int) (TOLERANCE_KM / DEFAULT_STEP_RATIO);
            for (int stride : STRIDE_VALUES) {
                results.addAll(runIterations(satellites, fixedStartTime, DEFAULT_PREPASS_KM,
                        stepSeconds, DEFAULT_STEP_RATIO, stride));
            }
            writeCsv(results, Paths.get("docs", "4-conjunction-coarse-interpolation", "conjunction_benchmark.csv"));
        }

        log.info("Benchmark complete");
        System.exit(0);
    }

    private List<BenchmarkResult> runIterations(List<Satellite> satellites, OffsetDateTime startTime,
                                                double prepassKm, int stepSeconds, int stepRatio, int stride)
            throws InterruptedException {
        List<BenchmarkResult> results = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            System.gc();
            Thread.sleep(100);
            results.add(runBenchmark(satellites, startTime, prepassKm,
                    stepSeconds, stepRatio, stride, i + 1));
        }
        return results;
    }

    private BenchmarkResult runBenchmark(List<Satellite> satellites, OffsetDateTime startTime,
                                         double prepassKm, int stepSeconds, int stepRatio, int stride, int iteration) {
        StopWatch total = StopWatch.createStarted();

        StopWatch pairReduction = StopWatch.createStarted();
        List<SatellitePair> pairs = pairReductionService.findPotentialCollisionPairs(satellites, prepassKm);
        pairReduction.stop();

        StopWatch filter = StopWatch.createStarted();
        List<Satellite> candidateSatellites = SatellitePair.uniqueSatellites(pairs, satellites);
        filter.stop();

        StopWatch propagator = StopWatch.createStarted();
        Map<Integer, TLEPropagator> propagators = propagationService.buildPropagators(candidateSatellites);
        propagator.stop();

        StopWatch propagateSweep = StopWatch.createStarted();
        PropagationService.KnotCache knots = propagationService.computeKnots(
                propagators, startTime, stepSeconds, LOOKAHEAD_HOURS, stride);
        propagateSweep.stop();

        StopWatch interpolation = StopWatch.createStarted();
        PropagationService.PositionCache positionCache = propagationService.interpolate(knots);
        interpolation.stop();

        StopWatch checkPairs = StopWatch.createStarted();
        List<ScanService.CoarseDetection> detections = scanService.checkPairs(pairs, positionCache, AccuracyBenchmark.TOLERANCE_KM);
        checkPairs.stop();

        StopWatch grouping = StopWatch.createStarted();
        Map<SatellitePair, List<List<ScanService.CoarseDetection>>> eventsByPair = scanService.groupIntoEvents(detections, stepSeconds);
        int totalEvents = eventsByPair.values().stream().mapToInt(List::size).sum();
        List<List<ScanService.CoarseDetection>> allEvents = eventsByPair.values().stream()
                .flatMap(List::stream)
                .toList();
        grouping.stop();

        StopWatch refine = StopWatch.createStarted();
        List<ScanService.RefinedEvent> refined = allEvents.parallelStream()
                .map(event -> scanService.refineEvent(event, positionCache, propagators, stepSeconds, THRESHOLD_KM))
                .filter(Objects::nonNull)
                .toList();
        refine.stop();

        StopWatch probability = StopWatch.createStarted();
        List<Conjunction> conjunctions = refined.parallelStream()
                .map(collisionProbabilityService::computeProbabilityAndBuild)
                .toList();
        probability.stop();

        total.stop();

        log.info("prepass={}km ratio={} stride={} iter={} | {}ms | pair={}ms filter={}ms prop={}ms sgp4={}ms interp={}ms check={}ms group={}ms refine={}ms pc={}ms | {} conj",
                (int) prepassKm, stepRatio, stride, iteration, total.getTime(),
                pairReduction.getTime(), filter.getTime(), propagator.getTime(),
                propagateSweep.getTime(), interpolation.getTime(), checkPairs.getTime(),
                grouping.getTime(), refine.getTime(), probability.getTime(),
                conjunctions.size());

        return new BenchmarkResult(AccuracyBenchmark.TOLERANCE_KM, prepassKm, stepSeconds, stepRatio, stride, iteration,
                detections.size(), totalEvents, conjunctions.size(),
                pairReduction.getTime(), filter.getTime(), propagator.getTime(), propagateSweep.getTime(),
                interpolation.getTime(), checkPairs.getTime(), grouping.getTime(), refine.getTime(),
                probability.getTime(), total.getTime());
    }

    private void writeCsv(List<BenchmarkResult> results, Path outputPath) {
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            writer.write("tolerance_km,prepass_km,step_s,step_ratio,interp_stride,iteration,detections,events,conj,pair_reduction_s,filter_s,propagator_s,sgp4_s,interp_s,check_s,grouping_s,refine_s,probability_s,total_s\n");

            for (BenchmarkResult r : results) {
                writer.write(String.format("%.0f,%.1f,%d,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                        r.toleranceKm, r.prepassKm, r.stepSeconds, r.stepRatio, r.stride, r.iteration,
                        r.detections, r.events, r.conjunctions,
                        r.pairReductionTime / 1000.0, r.filterTime / 1000.0,
                        r.propagatorTime / 1000.0, r.sgp4Time / 1000.0,
                        r.interpTime / 1000.0, r.checkTime / 1000.0,
                        r.groupingTime / 1000.0, r.refineTime / 1000.0,
                        r.probabilityTime / 1000.0, r.totalTime / 1000.0));
            }

            log.info("CSV written to: {}", outputPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write CSV file", e);
        }
    }

    private record BenchmarkResult(double toleranceKm, double prepassKm, int stepSeconds, int stepRatio, int stride,
                                   int iteration, long detections, int events, int conjunctions, long pairReductionTime,
                                   long filterTime, long propagatorTime, long sgp4Time, long interpTime, long checkTime,
                                   long groupingTime, long refineTime, long probabilityTime, long totalTime) {
    }
}
