package io.salad109.conjunctionapi.conjunction.internal;

import io.salad109.conjunctionapi.satellite.PairReductionService;
import io.salad109.conjunctionapi.satellite.Satellite;
import io.salad109.conjunctionapi.satellite.SatellitePair;
import io.salad109.conjunctionapi.satellite.SatelliteService;
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
import java.util.stream.Collectors;

/**
 * Run on Linux: ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-conjunction
 * Run on Windows: ./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-conjunction"
 */
@Component
@Profile("benchmark-conjunction")
public class ConjunctionBenchmark implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConjunctionBenchmark.class);

    private final SatelliteService satelliteService;
    private final PairReductionService pairReductionService;
    private final PropagationService propagationService;
    private final ScanService scanService;

    public ConjunctionBenchmark(SatelliteService satelliteService, PairReductionService pairReductionService, PropagationService propagationService, ScanService scanService) {
        this.satelliteService = satelliteService;
        this.pairReductionService = pairReductionService;
        this.propagationService = propagationService;
        this.scanService = scanService;
    }

    @Override
    public void run(String @NonNull ... args) throws InterruptedException {
        log.info("");
        log.info("Starting conjunction benchmark");
        log.info("");

        List<Satellite> satellites = satelliteService.getAll();
        log.info("Loaded {} satellites", satellites.size());

        // Use fixed start time
        OffsetDateTime fixedStartTime = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS);
        log.info("Using fixed start time: {}", fixedStartTime);

        Map<Integer, TLEPropagator> propagators = propagationService.buildPropagators(satellites);
        log.info("Built {} propagators", propagators.size());
        log.info("");

        // Benchmark parameters
        int lookaheadHours = 24;
        double thresholdKm = 5.0;
        double prepassToleranceKm = 12.5;
        int stepSecondRatio = 10;
        int interpolationStride = 6;

        List<BenchmarkResult> results = new ArrayList<>();

        while (true) {
            for (double toleranceKm = 60; toleranceKm <= 1200; toleranceKm += stepSecondRatio) {
                int stepSeconds = (int) (toleranceKm / stepSecondRatio);

                System.gc();
                Thread.sleep(100);
                results.add(runBenchmark(satellites, propagators, fixedStartTime,
                        toleranceKm, prepassToleranceKm, stepSeconds, stepSecondRatio,
                        lookaheadHours, thresholdKm, interpolationStride));
            }

            writeCsvResults(results);
        }
    }

    private BenchmarkResult runBenchmark(
            List<Satellite> satellites,
            Map<Integer, TLEPropagator> propagators,
            OffsetDateTime startTime,
            double toleranceKm,
            double prepassToleranceKm,
            int stepSeconds,
            int stepSecondRatio,
            int lookaheadHours,
            double thresholdKm,
            int interpolationStride
    ) {
        String name = String.format("tol=%.0f, prepass=%.1f, step=%d, stride=%d", toleranceKm, prepassToleranceKm, stepSeconds, interpolationStride);
        log.info("Running: {}", name);
        long benchmarkStart = System.nanoTime();

        // Step 1: Pair reduction
        long pairReductionStart = System.nanoTime();
        List<SatellitePair> pairs = pairReductionService.findPotentialCollisionPairs(satellites, prepassToleranceKm);
        long pairReductionTime = System.nanoTime() - pairReductionStart;
        log.info("Pair reduction completed in {}ms - {} candidate pairs", pairReductionTime / 1_000_000, pairs.size());

        // Step 2: Coarse sweep
        long coarseStart = System.nanoTime();
        List<ScanService.CoarseDetection> detections = scanService.coarseSweep(pairs, propagators, startTime, toleranceKm, stepSeconds, lookaheadHours, interpolationStride);
        long coarseTime = System.nanoTime() - coarseStart;
        log.info("Coarse sweep completed in {}ms - {} detections", coarseTime / 1_000_000, detections.size());

        // Step 3: Group into events
        long groupingStart = System.nanoTime();
        Map<SatellitePair, List<List<ScanService.CoarseDetection>>> eventsByPair = scanService.groupIntoEvents(detections, stepSeconds);
        int totalEvents = eventsByPair.values().stream().mapToInt(List::size).sum();
        List<List<ScanService.CoarseDetection>> allEvents = eventsByPair.values().stream()
                .flatMap(List::stream)
                .toList();
        long groupingTime = System.nanoTime() - groupingStart;
        log.info("Grouping completed in {}ms - {} events", groupingTime / 1_000_000, totalEvents);

        // Step 4: Refinement
        long refineStart = System.nanoTime();
        List<Conjunction> refined = allEvents.parallelStream().map(event -> scanService.refineEvent(event, propagators, stepSeconds, thresholdKm))
                .filter(c -> c.getMissDistanceKm() <= thresholdKm)
                .toList();
        long refineTime = System.nanoTime() - refineStart;
        log.info("Refinement completed in {}ms - {} conjunctions", refineTime / 1_000_000, refined.size());

        // Step 5: Deduplication
        long deduplicationStart = System.nanoTime();
        List<Conjunction> deduplicated = refined.stream()
                .collect(Collectors.toMap(
                        c -> c.getObject1NoradId() + ":" + c.getObject2NoradId(),
                        c -> c,
                        (a, b) -> a.getMissDistanceKm() <= b.getMissDistanceKm() ? a : b
                ))
                .values()
                .stream()
                .toList();
        long deduplicationTime = System.nanoTime() - deduplicationStart;
        log.info("Deduplication completed in {}ms - {} unique conjunctions", deduplicationTime / 1_000_000, deduplicated.size());

        long totalTime = System.nanoTime() - benchmarkStart;

        log.info("Total time: {}ms", totalTime / 1_000_000);
        log.info("");

        return new BenchmarkResult(name, toleranceKm, prepassToleranceKm, stepSeconds, stepSecondRatio,
                interpolationStride, detections.size(), totalEvents,
                refined.size(), deduplicated.size(), pairReductionTime, coarseTime,
                groupingTime, refineTime, deduplicationTime, totalTime);
    }

    private void writeCsvResults(List<BenchmarkResult> results) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "conjunction_benchmark_" + timestamp + ".csv";
        Path outputPath = Paths.get("docs", filename);

        try {
            try (FileWriter writer = new FileWriter(outputPath.toFile())) {
                writer.write("tolerance_km,prepass_km,step_s,step_ratio,interp_stride,detections,events,conj,dedup,pair_reduction_s,coarse_s,grouping_s,refine_s,dedup_s,total_s\n");

                for (BenchmarkResult r : results) {
                    writer.write(String.format("%.0f,%.1f,%d,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                            r.toleranceKm,
                            r.prepassToleranceKm,
                            r.stepSeconds,
                            r.stepSecondRatio,
                            r.interpolationStride,
                            r.detections,
                            r.events,
                            r.conjunctions,
                            r.deduplicated,
                            r.pairReductionTime / 1_000_000_000.0,
                            r.coarseTimeMs / 1_000_000_000.0,
                            r.groupingTime / 1_000_000_000.0,
                            r.refineTimeMs / 1_000_000_000.0,
                            r.deduplicationTime / 1_000_000_000.0,
                            r.totalTimeMs / 1_000_000_000.0));
                }
            }

            log.info("CSV results written to: {}", outputPath.toAbsolutePath());

        } catch (IOException e) {
            log.error("Failed to write CSV file", e);
        }
    }

    private record BenchmarkResult(String name, double toleranceKm, double prepassToleranceKm,
                                   int stepSeconds, int stepSecondRatio, int interpolationStride,
                                   long detections, int events, int conjunctions, int deduplicated,
                                   long pairReductionTime, long coarseTimeMs, long groupingTime,
                                   long refineTimeMs, long deduplicationTime, long totalTimeMs) {
    }
}
