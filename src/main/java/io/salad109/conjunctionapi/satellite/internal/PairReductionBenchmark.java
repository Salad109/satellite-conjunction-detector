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
import java.util.function.BiPredicate;
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
    private static final int ITERATIONS = 10;

    private final SatelliteRepository satelliteRepository;
    private final PairReductionService pairReductionService;

    public PairReductionBenchmark(SatelliteRepository satelliteRepository, PairReductionService pairReductionService) {
        this.satelliteRepository = satelliteRepository;
        this.pairReductionService = pairReductionService;
    }

    @Override
    public void run(String @NonNull ... args) throws InterruptedException {
        List<Satellite> satellites = satelliteRepository.findAll();
        int n = satellites.size();
        long totalPairs = (long) n * (n - 1) / 2;

        log.info("Loaded {} satellites ({} total pairs)", n, String.format("%,d", totalPairs));

        runOrderBenchmark(satellites, totalPairs);

        log.info("Benchmark complete");
    }


    private void runOrderBenchmark(List<Satellite> satellites, long totalPairs) throws InterruptedException {
        log.info("");
        log.info("Starting pair reduction order benchmark");
        log.info("");

        // A=altitude, D=debris, P=plane
        String[][] orderings = {
                {"A", "D", "P"},
                {"A", "P", "D"},
                {"D", "A", "P"},
                {"D", "P", "A"},
                {"P", "A", "D"},
                {"P", "D", "A"},
        };

        List<OrderResult> results = new ArrayList<>();

        for (int i = 0; i < ITERATIONS; i++) {
            for (String[] order : orderings) {
                System.gc();
                Thread.sleep(100);

                String orderName = String.join("", order);
                results.add(runOrderedChain(satellites, totalPairs, order, orderName, i + 1));
            }
        }

        writeCsv(results);
    }

    private OrderResult runOrderedChain(List<Satellite> satellites, long totalPairs,
                                        String[] order, String orderName, int iteration) {
        int n = satellites.size();

        BiPredicate<Satellite, Satellite> altitudeFilter =
                (a, b) -> pairReductionService.altitudeShellsOverlap(a, b, DEFAULT_TOLERANCE_KM);
        BiPredicate<Satellite, Satellite> debrisFilter =
                pairReductionService::neitherAreDebris;
        BiPredicate<Satellite, Satellite> planeFilter =
                (a, b) -> pairReductionService.orbitalPlanesIntersect(a, b, DEFAULT_TOLERANCE_KM);

        List<BiPredicate<Satellite, Satellite>> filters = new ArrayList<>(3);
        String[] filterNames = new String[3];

        for (int i = 0; i < 3; i++) {
            switch (order[i]) {
                case "A" -> {
                    filters.add(altitudeFilter);
                    filterNames[i] = "altitude";
                }
                case "D" -> {
                    filters.add(debrisFilter);
                    filterNames[i] = "debris";
                }
                case "P" -> {
                    filters.add(planeFilter);
                    filterNames[i] = "plane";
                }
            }
        }

        // Stage 1 (from all pairs)
        StopWatch watch1 = StopWatch.createStarted();
        List<SatellitePair> after1 = IntStream.range(0, n)
                .parallel()
                .boxed()
                .mapMulti((Integer i, Consumer<SatellitePair> consumer) -> {
                    Satellite a = satellites.get(i);
                    for (int j = i + 1; j < n; j++) {
                        Satellite b = satellites.get(j);
                        if (filters.getFirst().test(a, b)) {
                            consumer.accept(new SatellitePair(a, b));
                        }
                    }
                })
                .toList();
        watch1.stop();

        // Stage 2
        StopWatch watch2 = StopWatch.createStarted();
        List<SatellitePair> after2 = after1.parallelStream()
                .filter(pair -> filters.get(1).test(pair.a(), pair.b()))
                .toList();
        watch2.stop();

        // Stage 3
        StopWatch watch3 = StopWatch.createStarted();
        List<SatellitePair> after3 = after2.parallelStream()
                .filter(pair -> filters.get(2).test(pair.a(), pair.b()))
                .toList();
        watch3.stop();

        long totalMs = watch1.getTime() + watch2.getTime() + watch3.getTime();

        log.info("{} | {}ms | {}={}ms {}={}ms {}={}ms | {} final",
                orderName, totalMs,
                filterNames[0], watch1.getTime(),
                filterNames[1], watch2.getTime(),
                filterNames[2], watch3.getTime(),
                after3.size());

        return new OrderResult(orderName, iteration, totalPairs,
                filterNames[0], after1.size(), watch1.getTime(),
                filterNames[1], after2.size(), watch2.getTime(),
                filterNames[2], after3.size(), watch3.getTime(),
                totalMs);
    }

    private void writeCsv(List<OrderResult> results) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "pair_reduction_benchmark_" + timestamp + ".csv";
        Path outputPath = Paths.get("docs", filename);

        try {
            try (FileWriter writer = new FileWriter(outputPath.toFile())) {
                writer.write("order,iteration,total_pairs,filter1,after1,time1_ms,filter2,after2,time2_ms,filter3,after3,time3_ms,total_ms\n");

                for (OrderResult r : results) {
                    writer.write(String.format("%s,%d,%d,%s,%d,%d,%s,%d,%d,%s,%d,%d,%d%n",
                            r.order, r.iteration, r.totalPairs,
                            r.filter1, r.after1, r.time1Ms,
                            r.filter2, r.after2, r.time2Ms,
                            r.filter3, r.after3, r.time3Ms,
                            r.totalMs));
                }
            }

            log.info("CSV results written to: {}", outputPath.toAbsolutePath());

        } catch (IOException e) {
            log.error("Failed to write CSV file", e);
        }
    }

    private record OrderResult(String order, int iteration, long totalPairs,
                               String filter1, long after1, long time1Ms,
                               String filter2, long after2, long time2Ms,
                               String filter3, long after3, long time3Ms,
                               long totalMs) {
    }
}
