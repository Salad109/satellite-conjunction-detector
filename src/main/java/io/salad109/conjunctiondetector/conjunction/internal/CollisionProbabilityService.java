package io.salad109.conjunctiondetector.conjunction.internal;

import io.salad109.conjunctiondetector.conjunction.internal.ScanService.RefinedEvent;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class CollisionProbabilityService {

    // todo replace with historical TLE based model
    public Conjunction computeProbabilityAndBuild(RefinedEvent event) {
        double score = 0.0;
        if (event.pvA() != null && event.relativeVelocityKmS() > 0.01) { // matches ConjunctionRepository:24 threshold
            double t1 = tleAgeDays(event.pair().a().getEpoch(), event.tca());
            double t2 = tleAgeDays(event.pair().b().getEpoch(), event.tca());

            // score = e^(-d) / (1 + t1 + t2)
            score = Math.exp(-event.distanceKm()) / (1.0 + t1 + t2);
        }

        int object1 = Math.min(event.pair().a().getNoradCatId(), event.pair().b().getNoradCatId());
        int object2 = Math.max(event.pair().a().getNoradCatId(), event.pair().b().getNoradCatId());

        return new Conjunction(
                null,
                object1,
                object2,
                event.distanceKm(),
                event.tca(),
                event.relativeVelocityKmS(),
                score
        );
    }

    private double tleAgeDays(OffsetDateTime epoch, OffsetDateTime tca) {
        return Math.max(0, ChronoUnit.HOURS.between(epoch, tca) / 24.0);
    }
}
