package io.salad109.conjunctiondetector.satellite;

import java.time.OffsetDateTime;

public record SatelliteInfo(
        int noradCatId,
        String objectName,
        String objectType,
        OffsetDateTime epoch,
        String tleLine1,
        String tleLine2,
        double meanMotion,
        double eccentricity,
        double inclination,
        double raan,
        double argPerigee,
        double semiMajorAxisKm,
        double perigeeKm,
        double apogeeKm
) {
}
