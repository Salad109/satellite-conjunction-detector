package io.salad109.conjunctiondetector.satellite;

import java.math.BigDecimal;

public record SatelliteBriefInfo(
        int noradCatId,
        String objectName,
        String objectType,
        String countryCode,
        Double perigee,
        Double apogee,
        BigDecimal inclination,
        BigDecimal eccentricity,
        Double period,
        long conjunctions) {
}
