package io.salad109.conjunctiondetector.satellite.internal;

import io.salad109.conjunctiondetector.satellite.PairReductionService;
import io.salad109.conjunctiondetector.satellite.Satellite;
import io.salad109.conjunctiondetector.satellite.SatellitePair;
import org.apache.commons.lang3.time.StopWatch;
import org.jspecify.annotations.NonNull;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux:
 * cd src/main/c && make
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-filter-native -Dspring-boot.run.jvmArguments="-Xmx4g -Xms4g -XX:+AlwaysPreTouch -XX:+UseShenandoahGC --enable-native-access=ALL-UNNAMED"
 * Windows:
 * cd src/main/c && make
 * ./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-filter-native" "-Dspring-boot.run.jvmArguments=-Xmx4g -Xms4g -XX:+AlwaysPreTouch -XX:+UseShenandoahGC --enable-native-access=ALL-UNNAMED"
 */
@Component
@Profile("benchmark-filter-native")
public class PairReductionNativeBenchmark implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PairReductionNativeBenchmark.class);
    private static final double DEFAULT_TOLERANCE_KM = 12.5;
    private static final int ITERATIONS = 10;

    private final SatelliteRepository satelliteRepository;
    private final PairReductionService javaService;
    private final PairReductionNative nativeService;

    public PairReductionNativeBenchmark(
            SatelliteRepository satelliteRepository,
            PairReductionService javaService) {
        this.satelliteRepository = satelliteRepository;
        this.javaService = javaService;
        this.nativeService = new PairReductionNative();
    }

    @Override
    public void run(String @NonNull ... args) throws InterruptedException {
        log.info("");
        log.info("Starting Java vs C pair reduction benchmark");
        log.info("");

        List<Satellite> satellites = satelliteRepository.findAll();
        int n = satellites.size();
        long totalPairs = (long) n * (n - 1) / 2;
        log.info("Loaded {} satellites ({} total pairs)", n, String.format("%,d", totalPairs));

        // Benchmark phase
        log.info("Benchmark phase ({} iterations)...", ITERATIONS);
        List<BenchmarkResult> results = new ArrayList<>();

        for (int i = 0; i < ITERATIONS; i++) {
            log.info("Iteration {}/{}", i + 1, ITERATIONS);

            System.gc();
            Thread.sleep(100);

            StopWatch javaWatch = StopWatch.createStarted();
            List<SatellitePair> javaPairs = javaService.findPotentialCollisionPairsJava(
                    satellites, DEFAULT_TOLERANCE_KM);
            javaWatch.stop();

            log.info("Java: {}ms | {} pairs", javaWatch.getTime(), javaPairs.size());

            System.gc();
            Thread.sleep(100);

            StopWatch cWatch = StopWatch.createStarted();
            List<SatellitePair> cPairs = nativeService.findPairs(
                    satellites, DEFAULT_TOLERANCE_KM);
            cWatch.stop();

            log.info("C: {}ms | {} pairs", cWatch.getTime(), cPairs.size());

            results.add(new BenchmarkResult(
                    i + 1,
                    "Java",
                    totalPairs,
                    javaPairs.size(),
                    javaWatch.getTime()
            ));

            results.add(new BenchmarkResult(
                    i + 1,
                    "C",
                    totalPairs,
                    cPairs.size(),
                    cWatch.getTime()
            ));
        }

        writeCsv(results);
        log.info("Benchmark complete");
        System.exit(0);
    }

    private void writeCsv(List<BenchmarkResult> results) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "pair_reduction_native_" + timestamp + ".csv";
        Path outputPath = Paths.get("docs", filename);

        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            writer.write("iteration,implementation,total_pairs,final_pairs,time_ms\n");

            for (BenchmarkResult r : results) {
                writer.write(String.format("%d,%s,%d,%d,%d%n",
                        r.iteration, r.implementation, r.totalPairs, r.finalPairs, r.timeMs));
            }

            log.info("CSV results written to: {}", outputPath.toAbsolutePath());

        } catch (IOException e) {
            log.error("Failed to write CSV file", e);
        }
    }

    private record BenchmarkResult(
            int iteration,
            String implementation,
            long totalPairs,
            long finalPairs,
            long timeMs) {
    }
}
