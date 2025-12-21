package io.salad109.conjunctionapi.satellite;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

@Service
public class SatelliteService {

    SatelliteRepository satelliteRepository;

    public SatelliteService(SatelliteRepository satelliteRepository) {
        this.satelliteRepository = satelliteRepository;
    }

    public List<SatellitePair> findPotentialCollisionPairs(double toleranceKm) {
        List<Satellite> satellites = satelliteRepository.findAll();
        int satelliteCount = satellites.size();

        return IntStream.range(0, satelliteCount)
                .parallel()
                .boxed()
                .mapMulti((Integer i, Consumer<SatellitePair> consumer) -> {
                    Satellite a = satellites.get(i);
                    for (int j = i + 1; j < satelliteCount; j++) {
                        Satellite b = satellites.get(j);
                        if (PairReduction.canCollide(a, b, toleranceKm)) {
                            consumer.accept(new SatellitePair(a, b));
                        }
                    }
                })
                .toList();
    }
}
