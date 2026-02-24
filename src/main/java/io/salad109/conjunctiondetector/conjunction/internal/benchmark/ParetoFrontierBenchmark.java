package io.salad109.conjunctiondetector.conjunction.internal.benchmark;

import io.salad109.conjunctiondetector.conjunction.internal.CollisionProbabilityService;
import io.salad109.conjunctiondetector.conjunction.internal.PropagationService;
import io.salad109.conjunctiondetector.conjunction.internal.ScanService;
import io.salad109.conjunctiondetector.satellite.SatelliteScanInfo;
import io.salad109.conjunctiondetector.satellite.SatelliteService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux:
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-pareto -Dspring-boot.run.jvmArguments="-Xmx12g -Xms12g -XX:+AlwaysPreTouch"
 * Windows:
 * ./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-pareto" "-Dspring-boot.run.jvmArguments=-Xmx12g -Xms12g -XX:+AlwaysPreTouch"
 */
@Component
@Profile("benchmark-pareto")
public class ParetoFrontierBenchmark extends BenchmarkRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ParetoFrontierBenchmark.class);

    private static final double TOLERANCE_KM = 72.0;
    private static final double MIN_ACCURACY_PCT = 98.0;
    private static final int ITERATIONS = 2;

    // Starting values (safest)
    private static final int START_STEP_RATIO = 9;
    private static final int START_STRIDE = 10;
    private static final double START_CELL_RATIO = 1.0;

    // Step sizes
    private static final int STEP_RATIO_DELTA = 1;
    private static final int STRIDE_DELTA = 10;
    private static final double CELL_RATIO_DELTA = 0.15;

    public ParetoFrontierBenchmark(SatelliteService satelliteService, PropagationService propagationService,
                                   ScanService scanService, CollisionProbabilityService collisionProbabilityService) {
        super(satelliteService, propagationService, scanService, collisionProbabilityService);
    }

    private static double accuracyPct(BenchmarkResult result, int groundTruth) {
        return Math.min(100.0, (result.conjunctions() * 100.0) / groundTruth);
    }

    private static BenchmarkResult averageResults(List<BenchmarkResult> results) {
        int n = results.size();
        BenchmarkResult first = results.getFirst();
        return new BenchmarkResult(
                first.toleranceKm(), first.stepRatio(), first.cellRatio(), first.stride(),
                results.stream().mapToLong(BenchmarkResult::detections).sum() / n,
                results.stream().mapToInt(BenchmarkResult::events).sum() / n,
                results.stream().mapToInt(BenchmarkResult::conjunctions).sum() / n,
                results.stream().mapToLong(BenchmarkResult::propagatorTime).sum() / n,
                results.stream().mapToLong(BenchmarkResult::sgp4Time).sum() / n,
                results.stream().mapToLong(BenchmarkResult::interpTime).sum() / n,
                results.stream().mapToLong(BenchmarkResult::checkTime).sum() / n,
                results.stream().mapToLong(BenchmarkResult::groupingTime).sum() / n,
                results.stream().mapToLong(BenchmarkResult::refineTime).sum() / n,
                results.stream().mapToLong(BenchmarkResult::probabilityTime).sum() / n,
                results.stream().mapToLong(BenchmarkResult::totalTime).sum() / n);
    }

    @Override
    public void run(String @NonNull ... args) throws InterruptedException {
        log.info("");
        log.info("Starting Pareto frontier benchmark (bounded grid search, uncapped parameters)");
        log.info("Minimum accuracy threshold: {}%", MIN_ACCURACY_PCT);
        log.info("");

        List<SatelliteScanInfo> satellites = satelliteService.getAllScanInfo();
        log.info("Loaded {} satellites", satellites.size());
        log.info("Using fixed start time: {}", FIXED_START_TIME);

        BenchmarkResult groundTruthResult = runBenchmark(satellites, TOLERANCE_KM,
                START_STEP_RATIO, TOLERANCE_KM / START_STEP_RATIO, START_STRIDE, START_CELL_RATIO);
        int groundTruth = groundTruthResult.conjunctions();
        log.info("Ground truth: {} conjunctions ({}s)", groundTruth, groundTruthResult.totalTime() / 1000.0);

        List<BenchmarkResult> allResults = new ArrayList<>();
        allResults.add(groundTruthResult);

        int evaluated = 1;

        for (int stepRatio = START_STEP_RATIO; ; stepRatio -= STEP_RATIO_DELTA) {
            boolean anyValidAtThisStep = false;

            for (int stride = START_STRIDE; ; stride += STRIDE_DELTA) {
                boolean anyValidAtThisStride = false;

                for (double cellRatio = START_CELL_RATIO; ; cellRatio += CELL_RATIO_DELTA) {
                    // Skip the ground truth combo
                    if (stepRatio == START_STEP_RATIO && stride == START_STRIDE
                            && Math.abs(cellRatio - START_CELL_RATIO) < 0.001) {
                        continue;
                    }

                    double stepSeconds = TOLERANCE_KM / stepRatio;
                    List<BenchmarkResult> results = runIterations(satellites, TOLERANCE_KM,
                            stepRatio, stepSeconds, stride, cellRatio, ITERATIONS);
                    BenchmarkResult result = averageResults(results);
                    double acc = accuracyPct(result, groundTruth);
                    evaluated++;

                    log.info("[{}] stepRatio={} stride={} cellRatio={} | {} conj, {}% acc, {}s",
                            evaluated, stepRatio, stride,
                            String.format("%.1f", cellRatio),
                            result.conjunctions(),
                            String.format("%.2f", acc),
                            String.format("%.1f", result.totalTime() / 1000.0));

                    allResults.add(result);

                    if (acc >= MIN_ACCURACY_PCT) {
                        anyValidAtThisStride = true;
                        anyValidAtThisStep = true;
                    } else {
                        log.info("  Accuracy < {}%, pruning remaining cellRatio values", MIN_ACCURACY_PCT);
                        break;
                    }
                }

                if (!anyValidAtThisStride && stride > START_STRIDE) {
                    log.info("  No valid cellRatio at stride={}, pruning remaining stride values", stride);
                    break;
                }
            }

            if (!anyValidAtThisStep && stepRatio < START_STEP_RATIO) {
                log.info("  No valid combo at stepRatio={}, pruning remaining stepRatio values", stepRatio);
                break;
            }
        }

        log.info("");
        log.info("Grid search complete. {} points evaluated.", evaluated);

        writeCsv(allResults, Paths.get("docs", "5-pareto-frontier", "pareto_benchmark.csv"));

        log.info("Pareto frontier benchmark complete.");
        System.exit(0);
    }
}
