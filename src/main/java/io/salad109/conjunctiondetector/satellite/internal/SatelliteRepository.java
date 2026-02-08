package io.salad109.conjunctiondetector.satellite.internal;

import io.salad109.conjunctiondetector.satellite.Satellite;
import io.salad109.conjunctiondetector.satellite.SatelliteInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface SatelliteRepository extends JpaRepository<Satellite, Integer> {

    /**
     * Count satellites in catalog.
     */
    long count();

    int deleteSatellitesByNoradCatIdNotIn(Collection<Integer> id);

    Optional<SatelliteInfo> findSatelliteInfoByNoradCatId(int noradCatId);
}
