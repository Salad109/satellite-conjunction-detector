package io.salad109.conjunctiondetector.conjunction.internal.benchmark;

import io.salad109.conjunctiondetector.conjunction.internal.CollisionProbabilityService;
import io.salad109.conjunctiondetector.conjunction.internal.Conjunction;
import io.salad109.conjunctiondetector.conjunction.internal.PropagationService;
import io.salad109.conjunctiondetector.conjunction.internal.ScanService;
import io.salad109.conjunctiondetector.satellite.SatelliteScanInfo;
import io.salad109.conjunctiondetector.satellite.SatelliteScanInfoPair;
import io.salad109.conjunctiondetector.satellite.SatelliteService;
import org.apache.commons.lang3.time.StopWatch;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class BenchmarkRunner {

    protected static final int LOOKAHEAD_HOURS = 24;
    protected static final double THRESHOLD_KM = 5.0;
    protected static final OffsetDateTime FIXED_START_TIME = OffsetDateTime
            .of(2026, 2, 23, 0, 40, 0, 0, ZoneOffset.UTC);
    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);
    protected final SatelliteService satelliteService;
    protected final PropagationService propagationService;
    protected final ScanService scanService;
    protected final CollisionProbabilityService collisionProbabilityService;

    protected BenchmarkRunner(SatelliteService satelliteService, PropagationService propagationService,
                              ScanService scanService, CollisionProbabilityService collisionProbabilityService) {
        this.satelliteService = satelliteService;
        this.propagationService = propagationService;
        this.scanService = scanService;
        this.collisionProbabilityService = collisionProbabilityService;
    }

    protected BenchmarkResult runBenchmark(List<SatelliteScanInfo> satellites,
                                           double toleranceKm, int stepRatio, double stepSeconds,
                                           int stride, double cellRatio) {
        double cellSizeKm = toleranceKm / cellRatio;
        StopWatch total = StopWatch.createStarted();

        StopWatch propagator = StopWatch.createStarted();
        Map<Integer, TLEPropagator> propagators = propagationService.buildPropagators(satellites);
        propagator.stop();

        StopWatch propagateSweep = StopWatch.createStarted();
        PropagationService.KnotCache knots = propagationService.computeKnots(
                propagators, BenchmarkRunner.FIXED_START_TIME, stepSeconds, LOOKAHEAD_HOURS, stride);
        propagateSweep.stop();

        StopWatch interpolation = StopWatch.createStarted();
        PropagationService.PositionCache positionCache = propagationService.interpolate(knots);
        interpolation.stop();

        StopWatch checkPairs = StopWatch.createStarted();
        List<ScanService.CoarseDetection> detections = scanService.checkPairs(satellites, positionCache, toleranceKm, cellSizeKm);
        checkPairs.stop();

        StopWatch grouping = StopWatch.createStarted();
        Map<SatelliteScanInfoPair, List<List<ScanService.CoarseDetection>>> eventsByPair = scanService.groupIntoEvents(detections);
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

        log.info("tol={}km stepRatio={} stride={} cellRatio={} | {}ms | prop={}ms sgp4={}ms interp={}ms check={}ms group={}ms refine={}ms pc={}ms | {} conj",
                (int) toleranceKm, stepRatio, stride, cellRatio, total.getTime(),
                propagator.getTime(), propagateSweep.getTime(), interpolation.getTime(),
                checkPairs.getTime(), grouping.getTime(), refine.getTime(),
                probability.getTime(), conjunctions.size());

        return new BenchmarkResult(toleranceKm, stepRatio, cellRatio, stride,
                detections.size(), totalEvents, conjunctions.size(),
                propagator.getTime(), propagateSweep.getTime(),
                interpolation.getTime(), checkPairs.getTime(), grouping.getTime(), refine.getTime(),
                probability.getTime(), total.getTime());
    }

    protected List<BenchmarkResult> runIterations(List<SatelliteScanInfo> satellites,
                                                  double toleranceKm, int stepRatio, double stepSeconds,
                                                  int stride, double cellRatio, int iterations) throws InterruptedException {
        List<BenchmarkResult> results = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            System.gc();
            Thread.sleep(100);
            results.add(runBenchmark(satellites, toleranceKm, stepRatio, stepSeconds, stride, cellRatio));
        }
        return results;
    }

    protected void writeCsv(List<BenchmarkResult> results, Path outputPath) {
        String csv = buildCsv(results);
        //noinspection ResultOfMethodCallIgnored
        outputPath.getParent().toFile().mkdirs();
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            writer.write(csv);
            log.info("CSV written to: {}", outputPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write CSV to {}: {}", outputPath, e.getMessage());
            log.error("Dumping results to log:");
            log.error(csv);
        }
    }

    private String buildCsv(List<BenchmarkResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("tolerance_km,step_ratio,cell_ratio,interp_stride,detections,events,conj,propagator_s,sgp4_s,interp_s,check_s,grouping_s,refine_s,probability_s,total_s\n");
        for (BenchmarkResult r : results) {
            sb.append(String.format("%.0f,%d,%.2f,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                    r.toleranceKm, r.stepRatio, r.cellRatio, r.stride,
                    r.detections, r.events, r.conjunctions,
                    r.propagatorTime / 1000.0, r.sgp4Time / 1000.0,
                    r.interpTime / 1000.0, r.checkTime / 1000.0,
                    r.groupingTime / 1000.0, r.refineTime / 1000.0,
                    r.probabilityTime / 1000.0, r.totalTime / 1000.0));
        }
        return sb.toString();
    }

    public record BenchmarkResult(double toleranceKm, int stepRatio, double cellRatio, int stride,
                                  long detections, int events, int conjunctions,
                                  long propagatorTime, long sgp4Time, long interpTime, long checkTime,
                                  long groupingTime, long refineTime, long probabilityTime, long totalTime) {
    }
}
