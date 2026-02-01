package io.salad109.conjunctionapi.satellite.internal;

import io.salad109.conjunctionapi.satellite.PairReductionService;
import io.salad109.conjunctionapi.satellite.Satellite;
import io.salad109.conjunctionapi.satellite.SatellitePair;
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
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Linux:
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-filter -Dspring-boot.run.jvmArguments="-XX:+UseZGC -Xmx16g -Xms16g -XX:+AlwaysPreTouch"
 * Windows:
 * ./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-filter" "-Dspring-boot.run.jvmArguments=-XX:+UseZGC -Xmx16g -Xms16g -XX:+AlwaysPreTouch"
 */
@Component
@Profile("benchmark-filter")
public class PairReductionBenchmark implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PairReductionBenchmark.class);
    private static final double DEFAULT_TOLERANCE_KM = 12.5;
    private static final int ITERATIONS = 20;

    private final SatelliteRepository satelliteRepository;
    private final PairReductionService pairReductionService;

    private final Map<String, PairFilter> orderings;

    public PairReductionBenchmark(SatelliteRepository satelliteRepository, PairReductionService pairReductionService) {
        this.satelliteRepository = satelliteRepository;
        this.pairReductionService = pairReductionService;

        // A=altitude, D=debris, P=plane
        this.orderings = Map.of(
                "ADP", (a, b, tol) ->
                        this.pairReductionService.altitudeShellsOverlap(a, b, tol) &&
                                this.pairReductionService.neitherAreDebris(a, b) &&
                                this.pairReductionService.orbitalPlanesIntersect(a, b, tol),
                "APD", (a, b, tol) ->
                        this.pairReductionService.altitudeShellsOverlap(a, b, tol) &&
                                this.pairReductionService.orbitalPlanesIntersect(a, b, tol) &&
                                this.pairReductionService.neitherAreDebris(a, b),
                "DAP", (a, b, tol) ->
                        this.pairReductionService.neitherAreDebris(a, b) &&
                                this.pairReductionService.altitudeShellsOverlap(a, b, tol) &&
                                this.pairReductionService.orbitalPlanesIntersect(a, b, tol),
                "DPA", (a, b, tol) ->
                        this.pairReductionService.neitherAreDebris(a, b) &&
                                this.pairReductionService.orbitalPlanesIntersect(a, b, tol) &&
                                this.pairReductionService.altitudeShellsOverlap(a, b, tol),
                "PAD", (a, b, tol) ->
                        this.pairReductionService.orbitalPlanesIntersect(a, b, tol) &&
                                this.pairReductionService.altitudeShellsOverlap(a, b, tol) &&
                                this.pairReductionService.neitherAreDebris(a, b),
                "PDA", (a, b, tol) ->
                        this.pairReductionService.orbitalPlanesIntersect(a, b, tol) &&
                                this.pairReductionService.neitherAreDebris(a, b) &&
                                this.pairReductionService.altitudeShellsOverlap(a, b, tol)
        );
    }

    @Override
    public void run(String @NonNull ... args) throws InterruptedException {
        List<Satellite> satellites = satelliteRepository.findAll();
        int n = satellites.size();
        long totalPairs = (long) n * (n - 1) / 2;

        log.info("Loaded {} satellites ({} total pairs)", n, String.format("%,d", totalPairs));
        log.info("");
        log.info("Starting pair reduction order benchmark");
        log.info("");

        List<String> orderNames = List.of("ADP", "APD", "DAP", "DPA", "PAD", "PDA");
        List<OrderResult> results = new ArrayList<>();

        for (int i = 0; i < ITERATIONS; i++) {
            log.info("Iteration {}/{}", i + 1, ITERATIONS);
            for (String orderName : orderNames) {
                System.gc();
                Thread.sleep(100);

                results.add(runOrdering(satellites, totalPairs, orderName));
            }
        }

        writeCsv(results);
        log.info("Benchmark complete");
    }

    private OrderResult runOrdering(List<Satellite> satellites, long totalPairs,
                                    String orderName) {
        int n = satellites.size();
        PairFilter filter = orderings.get(orderName);

        StopWatch watch = StopWatch.createStarted();
        List<SatellitePair> results = IntStream.range(0, n)
                .parallel()
                .boxed()
                .mapMulti((Integer i, Consumer<SatellitePair> consumer) -> {
                    Satellite a = satellites.get(i);
                    for (int j = i + 1; j < n; j++) {
                        Satellite b = satellites.get(j);
                        if (filter.test(a, b, DEFAULT_TOLERANCE_KM)) {
                            consumer.accept(new SatellitePair(a, b));
                        }
                    }
                })
                .toList();
        watch.stop();

        log.info("{} | {}ms | {} pairs", orderName, watch.getTime(), results.size());

        return new OrderResult(orderName, totalPairs, results.size(), watch.getTime());
    }

    private void writeCsv(List<OrderResult> results) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "pair_reduction_benchmark_" + timestamp + ".csv";
        Path outputPath = Paths.get("docs", filename);

        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            writer.write("order,total_pairs,final_pairs,time_ms\n");

            for (OrderResult r : results) {
                writer.write(String.format("%s,%d,%d,%d%n",
                        r.order, r.totalPairs, r.finalPairs, r.timeMs));
            }

            log.info("CSV results written to: {}", outputPath.toAbsolutePath());

        } catch (IOException e) {
            log.error("Failed to write CSV file", e);
        }
    }

    @FunctionalInterface
    interface PairFilter {
        boolean test(Satellite a, Satellite b, double toleranceKm);
    }

    private record OrderResult(String order, long totalPairs, long finalPairs, long timeMs) {
    }
}
