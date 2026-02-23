package io.salad109.conjunctiondetector.satellite.internal;

import io.salad109.conjunctiondetector.satellite.Satellite;
import io.salad109.conjunctiondetector.satellite.SatelliteInfo;
import io.salad109.conjunctiondetector.satellite.SatelliteScanInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SatelliteRepository extends JpaRepository<Satellite, Integer> {

    /**
     * Count satellites in catalog.
     */
    long count();

    int deleteSatellitesByNoradCatIdNotIn(Collection<Integer> id);

    Optional<SatelliteInfo> findSatelliteInfoByNoradCatId(int noradCatId);

    @Query("SELECT new io.salad109.conjunctiondetector.satellite.SatelliteScanInfo(" +
            "s.noradCatId, s.tleLine1, s.tleLine2, s.epoch, s.perigeeKm, s.objectType) " +
            "FROM Satellite s")
    List<SatelliteScanInfo> findAllSatelliteScanInfo();
}
