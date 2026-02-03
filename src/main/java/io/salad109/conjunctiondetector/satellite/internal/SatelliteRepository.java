package io.salad109.conjunctiondetector.satellite.internal;

import io.salad109.conjunctiondetector.satellite.Satellite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface SatelliteRepository extends JpaRepository<Satellite, Integer> {

    /**
     * Count satellites in catalog.
     */
    long count();

    int deleteSatellitesByNoradCatIdNotIn(Collection<Integer> id);
}
