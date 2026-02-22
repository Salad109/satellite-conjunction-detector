package io.salad109.conjunctiondetector.conjunction.internal.benchmark;

import io.salad109.conjunctiondetector.conjunction.internal.CollisionProbabilityService;
import io.salad109.conjunctiondetector.conjunction.internal.PropagationService;
import io.salad109.conjunctiondetector.conjunction.internal.ScanService;
import io.salad109.conjunctiondetector.satellite.Satellite;
import io.salad109.conjunctiondetector.satellite.SatelliteService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux:
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-conjunction -Dspring-boot.run.jvmArguments="-Xmx10g -Xms10g -XX:+AlwaysPreTouch -XX:+UseShenandoahGC"
 * Windows:
 * ./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-conjunction" "-Dspring-boot.run.jvmArguments=-Xmx10g -Xms10g -XX:+AlwaysPreTouch -XX:+UseShenandoahGC"
 */
@Component
@Profile("benchmark-conjunction")
public class ConjunctionBenchmark extends BenchmarkRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConjunctionBenchmark.class);

    private static final int ITERATIONS = 5;
    private static final int STEP_RATIO = 10;
    private static final int INTERPOLATION_STRIDE = 50;
    private static final double CELL_RATIO = 1.3;

    private static final double[] TOLERANCE_VALUES = {24, 32, 40, 48, 56, 64, 72, 80, 88, 96, 104, 112, 120, 128, 136, 144, 152, 160};

    public ConjunctionBenchmark(SatelliteService satelliteService, PropagationService propagationService,
                                ScanService scanService, CollisionProbabilityService collisionProbabilityService) {
        super(satelliteService, propagationService, scanService, collisionProbabilityService);
    }

    @Override
    public void run(String @NonNull ... args) throws InterruptedException {
        log.info("");
        log.info("Starting conjunction benchmark");
        log.info("");

        List<Satellite> satellites = satelliteService.getAll();
        log.info("Loaded {} satellites", satellites.size());

        log.info("Using fixed start time: {}", FIXED_START_TIME);
        log.info("Locked: stepRatio={}, stride={}, cellRatio={}", STEP_RATIO, INTERPOLATION_STRIDE, CELL_RATIO);

        List<BenchmarkResult> results = new ArrayList<>();

        for (double toleranceKm : TOLERANCE_VALUES) {
            double stepSeconds = toleranceKm / STEP_RATIO;
            results.addAll(runIterations(satellites, toleranceKm, STEP_RATIO, stepSeconds, INTERPOLATION_STRIDE, CELL_RATIO, ITERATIONS));
        }

        writeCsvResults(results);

        log.info("Benchmark complete");
        System.exit(0);
    }

    private void writeCsvResults(List<BenchmarkResult> results) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "conjunction_benchmark_" + timestamp + ".csv";
        writeCsv(results, Paths.get("docs", "5-conjunction-tolerance", filename));
    }
}
