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
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Linux:
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-filter -Dspring-boot.run.jvmArguments="-Xmx4g -Xms4g -XX:+AlwaysPreTouch -XX:+UseShenandoahGC"
 * Windows:
 * ./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-filter" "-Dspring-boot.run.jvmArguments=-Xmx4g -Xms4g -XX:+AlwaysPreTouch -XX:+UseShenandoahGC"
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
        log.info("");
        log.info("Starting pair reduction order benchmark");
        log.info("");

        double[] perigees = new double[n];
        double[] apogees = new double[n];
        double[] inclinations = new double[n];
        double[] raans = new double[n];
        double[] argPerigees = new double[n];
        double[] eccentricities = new double[n];
        double[] semiMajorAxes = new double[n];
        boolean[] isDebris = new boolean[n];

        for (int i = 0; i < n; i++) {
            Satellite s = satellites.get(i);
            perigees[i] = s.getPerigeeKm();
            apogees[i] = s.getApogeeKm();
            inclinations[i] = Math.toRadians(s.getInclination());
            raans[i] = Math.toRadians(s.getRaan());
            argPerigees[i] = Math.toRadians(s.getArgPerigee());
            eccentricities[i] = s.getEccentricity();
            semiMajorAxes[i] = s.getSemiMajorAxisKm();
            isDebris[i] = "DEBRIS".equals(s.getObjectType());
        }

        // A=altitude, D=debris, P=plane
        List<String> orderNames = List.of("ADP", "APD", "DAP", "DPA", "PAD", "PDA");
        List<OrderResult> results = new ArrayList<>();

        for (int i = 0; i < ITERATIONS; i++) {
            log.info("Iteration {}/{}", i + 1, ITERATIONS);
            for (String orderName : orderNames) {
                System.gc();
                Thread.sleep(100);

                results.add(runOrdering(satellites, totalPairs, orderName, n,
                        perigees, apogees, inclinations, raans, argPerigees,
                        eccentricities, semiMajorAxes, isDebris));
            }
        }

        writeCsv(results);
        log.info("Benchmark complete");
        System.exit(0);
    }

    private OrderResult runOrdering(List<Satellite> satellites, long totalPairs, String orderName,
                                    int n, double[] perigees, double[] apogees,
                                    double[] inclinations, double[] raans, double[] argPerigees,
                                    double[] eccentricities, double[] semiMajorAxes, boolean[] isDebris) {

        StopWatch watch = StopWatch.createStarted();
        List<SatellitePair> results = IntStream.range(0, n)
                .parallel()
                .boxed()
                .mapMulti((Integer i, Consumer<SatellitePair> consumer) -> {
                    double perigeeA = perigees[i];
                    double apogeeA = apogees[i];
                    boolean debrisA = isDebris[i];
                    double iA = inclinations[i];
                    double raanA = raans[i];
                    double omegaA = argPerigees[i];
                    double eA = eccentricities[i];
                    double aA = semiMajorAxes[i];

                    for (int j = i + 1; j < n; j++) {
                        if (shouldSkip(orderName, DEFAULT_TOLERANCE_KM,
                                perigeeA, apogeeA, debrisA, iA, raanA, omegaA, eA, aA,
                                perigees[j], apogees[j], isDebris[j],
                                inclinations[j], raans[j], argPerigees[j],
                                eccentricities[j], semiMajorAxes[j])) {
                            continue;
                        }
                        consumer.accept(new SatellitePair(satellites.get(i), satellites.get(j)));
                    }
                })
                .toList();
        watch.stop();

        log.info("{} | {}ms | {} pairs", orderName, watch.getTime(), results.size());

        return new OrderResult(orderName, totalPairs, results.size(), watch.getTime());
    }

    private boolean shouldSkip(String order, double tol,
                               double perigeeA, double apogeeA, boolean debrisA,
                               double iA, double raanA, double omegaA, double eA, double aA,
                               double perigeeB, double apogeeB, boolean debrisB,
                               double iB, double raanB, double omegaB, double eB, double aB) {
        return switch (order) {
            case "ADP" -> pairReductionService.altitudeShellsMiss(perigeeA, apogeeA, perigeeB, apogeeB, tol)
                    || pairReductionService.bothDebris(debrisA, debrisB)
                    || pairReductionService.orbitalPlanesMiss(iA, raanA, omegaA, eA, aA, iB, raanB, omegaB, eB, aB, tol);
            case "APD" -> pairReductionService.altitudeShellsMiss(perigeeA, apogeeA, perigeeB, apogeeB, tol)
                    || pairReductionService.orbitalPlanesMiss(iA, raanA, omegaA, eA, aA, iB, raanB, omegaB, eB, aB, tol)
                    || pairReductionService.bothDebris(debrisA, debrisB);
            case "DAP" -> pairReductionService.bothDebris(debrisA, debrisB)
                    || pairReductionService.altitudeShellsMiss(perigeeA, apogeeA, perigeeB, apogeeB, tol)
                    || pairReductionService.orbitalPlanesMiss(iA, raanA, omegaA, eA, aA, iB, raanB, omegaB, eB, aB, tol);
            case "DPA" -> pairReductionService.bothDebris(debrisA, debrisB)
                    || pairReductionService.orbitalPlanesMiss(iA, raanA, omegaA, eA, aA, iB, raanB, omegaB, eB, aB, tol)
                    || pairReductionService.altitudeShellsMiss(perigeeA, apogeeA, perigeeB, apogeeB, tol);
            case "PAD" ->
                    pairReductionService.orbitalPlanesMiss(iA, raanA, omegaA, eA, aA, iB, raanB, omegaB, eB, aB, tol)
                            || pairReductionService.altitudeShellsMiss(perigeeA, apogeeA, perigeeB, apogeeB, tol)
                            || pairReductionService.bothDebris(debrisA, debrisB);
            case "PDA" ->
                    pairReductionService.orbitalPlanesMiss(iA, raanA, omegaA, eA, aA, iB, raanB, omegaB, eB, aB, tol)
                            || pairReductionService.bothDebris(debrisA, debrisB)
                            || pairReductionService.altitudeShellsMiss(perigeeA, apogeeA, perigeeB, apogeeB, tol);
            default -> throw new IllegalArgumentException("Unknown order: " + order);
        };
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

    private record OrderResult(String order, long totalPairs, long finalPairs, long timeMs) {
    }
}
