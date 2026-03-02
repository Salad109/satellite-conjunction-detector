package io.salad109.conjunctiondetector.satellite.internal;

import io.salad109.conjunctiondetector.satellite.Satellite;
import io.salad109.conjunctiondetector.satellite.SatelliteBriefInfo;
import io.salad109.conjunctiondetector.satellite.SatelliteDetails;
import io.salad109.conjunctiondetector.satellite.SatelliteScanInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<SatelliteDetails> findSatelliteDetailsByNoradCatId(int noradCatId);

    @Query("SELECT new io.salad109.conjunctiondetector.satellite.SatelliteScanInfo(" +
            "s.noradCatId, s.tleLine1, s.tleLine2, s.epoch, s.perigeeKm, s.objectType) " +
            "FROM Satellite s")
    List<SatelliteScanInfo> findAllSatelliteScanInfo();

    @Query("SELECT new io.salad109.conjunctiondetector.satellite.SatelliteBriefInfo(" +
            "s.noradCatId, s.objectName, s.objectType, s.countryCode, s.perigeeKm, s.apogeeKm, " +
            "s.inclination, s.eccentricity, s.period) " +
            "FROM Satellite s")
    Page<SatelliteBriefInfo> getSatelliteBriefInfos(Pageable pageable);
}
