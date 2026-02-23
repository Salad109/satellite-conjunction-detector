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
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-accuracy -Dspring-boot.run.jvmArguments="-Xmx12g -Xms12g -XX:+AlwaysPreTouch -XX:+UseShenandoahGC"
 * Windows:
 * ./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-accuracy" "-Dspring-boot.run.jvmArguments=-Xmx12g -Xms12g -XX:+AlwaysPreTouch -XX:+UseShenandoahGC"
 */
@Component
@Profile("benchmark-accuracy")
public class AccuracyBenchmark extends BenchmarkRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AccuracyBenchmark.class);

    private static final int ITERATIONS = 3;
    private static final double TOLERANCE_KM = 64.0;

    private static final int DEFAULT_STEP_RATIO = 10;
    private static final int DEFAULT_STRIDE = 30;
    private static final double DEFAULT_CELL_RATIO = 1.30;

    private static final int[] STEP_RATIO_VALUES = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    private static final int[] STRIDE_VALUES = {1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130, 135, 140, 145, 150, 155, 160, 165, 170, 175, 180, 185, 190, 195, 200};
    private static final double[] CELL_RATIO_VALUES = {0.5, 0.6, 0.7, 0.8, 0.9, 1, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2};

    public AccuracyBenchmark(SatelliteService satelliteService, PropagationService propagationService,
                             ScanService scanService, CollisionProbabilityService collisionProbabilityService) {
        super(satelliteService, propagationService, scanService, collisionProbabilityService);
    }

    @Override
    public void run(String @NonNull ... args) throws InterruptedException {
        log.info("");
        log.info("Starting conjunction accuracy benchmark");
        log.info("");

        List<SatelliteScanInfo> satellites = satelliteService.getAllScanInfo();
        log.info("Loaded {} satellites", satellites.size());

        log.info("Using fixed start time: {}", FIXED_START_TIME);
        log.info("Fixed tolerance: {} km, threshold: {} km, lookahead: {} h",
                TOLERANCE_KM, THRESHOLD_KM, LOOKAHEAD_HOURS);

        log.info("");
        log.info("Sweeping step ratio");
        log.info("Locked: stride={}, cellRatio={}", DEFAULT_STRIDE, DEFAULT_CELL_RATIO);
        {
            List<BenchmarkResult> results = new ArrayList<>();
            for (int stepRatio : STEP_RATIO_VALUES) {
                double stepSeconds = TOLERANCE_KM / stepRatio;
                results.addAll(runIterations(satellites, TOLERANCE_KM, stepRatio, stepSeconds, DEFAULT_STRIDE, DEFAULT_CELL_RATIO, ITERATIONS));
            }
            writeCsv(results, Paths.get("docs", "1-step-ratio", "conjunction_benchmark.csv"));
        }

        log.info("");
        log.info("Sweeping interpolation stride");
        log.info("Locked: stepRatio={}, cellRatio={}", DEFAULT_STEP_RATIO, DEFAULT_CELL_RATIO);
        {
            List<BenchmarkResult> results = new ArrayList<>();
            double stepSeconds = TOLERANCE_KM / DEFAULT_STEP_RATIO;
            for (int stride : STRIDE_VALUES) {
                results.addAll(runIterations(satellites, TOLERANCE_KM, DEFAULT_STEP_RATIO, stepSeconds, stride, DEFAULT_CELL_RATIO, ITERATIONS));
            }
            writeCsv(results, Paths.get("docs", "2-interpolation-stride", "conjunction_benchmark.csv"));
        }

        log.info("");
        log.info("Sweeping cell ratio");
        log.info("Locked: stepRatio={}, stride={}", DEFAULT_STEP_RATIO, DEFAULT_STRIDE);
        {
            List<BenchmarkResult> results = new ArrayList<>();
            double stepSeconds = TOLERANCE_KM / DEFAULT_STEP_RATIO;
            for (double cellRatio : CELL_RATIO_VALUES) {
                results.addAll(runIterations(satellites, TOLERANCE_KM, DEFAULT_STEP_RATIO, stepSeconds, DEFAULT_STRIDE, cellRatio, ITERATIONS));
            }
            writeCsv(results, Paths.get("docs", "3-cell-size-ratio", "conjunction_benchmark.csv"));
        }

        log.info("Benchmark complete");
        System.exit(0);
    }

}
