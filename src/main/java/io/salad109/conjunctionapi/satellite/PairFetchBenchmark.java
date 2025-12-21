package io.salad109.conjunctionapi.satellite;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Benchmarks the full service method including data fetching and filtering.
 * Run on Linux: ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark-fetch
 * Run on Windows: ./mvnw spring-boot:run "-Dspring-boot.run.profiles=benchmark-fetch"
 */
@Component
@Profile("benchmark-fetch")
public class PairFetchBenchmark implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PairFetchBenchmark.class);
    private static final double TOLERANCE_KM = 10.0;

    private final SatelliteService satelliteService;
    private final SatelliteRepository satelliteRepository;

    public PairFetchBenchmark(SatelliteService satelliteService, SatelliteRepository satelliteRepository) {
        this.satelliteService = satelliteService;
        this.satelliteRepository = satelliteRepository;
    }

    @Override
    public void run(String @NonNull ... args) {
        log.info("Starting pair fetch benchmark");

        int satelliteCount = (int) satelliteRepository.count();
        long totalPairs = (long) satelliteCount * (satelliteCount - 1) / 2;

        log.info("Loaded {} satellites", satelliteCount);

        long startTime = System.currentTimeMillis();

        List<SatellitePair> satellites = satelliteService.findPotentialCollisionPairs(TOLERANCE_KM);

        long elapsed = System.currentTimeMillis() - startTime;

        log.info("");
        log.info("Benchmark results:");
        log.info("Total pairs: {}",
                String.format("%,d", totalPairs));
        log.info("Remaining candidates: {} ({}%)",
                String.format("%,d", satellites.size()),
                String.format("%.2f", 100.0 * satellites.size() / totalPairs));
        log.info("Total reduction: {}%",
                String.format("%.2f", 100.0 * (1 - (double) satellites.size() / totalPairs)));
        log.info("Elapsed time: {} seconds",
                String.format("%.2f", elapsed / 1000.0));
        log.info("Throughput: {} pairs/second",
                String.format("%,.0f", (double) totalPairs / (elapsed / 1000.0)));
    }
}
