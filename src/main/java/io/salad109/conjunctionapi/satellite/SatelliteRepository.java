package io.salad109.conjunctionapi.satellite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SatelliteRepository extends JpaRepository<Satellite, Integer> {

    /**
     * Count satellites in catalog.
     */
    long count();

    /**
     * Find objects with overlapping orbital shells (apogee-perigee filter).
     * This is the key optimization for conjunction screening.
     * Returns pairs where their orbital altitudes could intersect.
     */
    @Query(value = """
            SELECT s1.norad_cat_id as obj1, s2.norad_cat_id as obj2
            FROM satellite s1
            JOIN satellite s2 ON s1.norad_cat_id < s2.norad_cat_id
            WHERE s1.perigee_km <= s2.apogee_km + :threshold
              AND s2.perigee_km <= s1.apogee_km + :threshold
            """, nativeQuery = true)
    List<Object[]> findOverlappingOrbitalShells(@Param("threshold") double thresholdKm);

    /**
     * Find objects in a specific altitude band.
     */
    @Query("SELECT s FROM Satellite s WHERE s.perigeeKm >= :minAlt AND s.apogeeKm <= :maxAlt")
    List<Satellite> findInAltitudeBand(@Param("minAlt") double minAltKm, @Param("maxAlt") double maxAltKm);
}
