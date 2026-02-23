package io.salad109.conjunctiondetector.satellite;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record SatelliteInfo(
        int noradCatId,
        String objectName,
        String objectId,
        String objectType,
        String classificationType,
        String countryCode,
        LocalDate launchDate,
        String site,
        LocalDate decayDate,
        OffsetDateTime epoch,
        LocalDateTime creationDate,
        String tleLine0,
        String tleLine1,
        String tleLine2,
        BigDecimal meanMotion,
        BigDecimal meanMotionDot,
        BigDecimal meanMotionDdot,
        BigDecimal eccentricity,
        BigDecimal inclination,
        BigDecimal raan,
        BigDecimal argPerigee,
        BigDecimal meanAnomaly,
        Integer ephemerisType,
        BigDecimal bstar,
        String rcsSize,
        Integer elementSetNo,
        Integer revAtEpoch,
        Double semiMajorAxisKm,
        Double period,
        Double perigeeKm,
        Double apogeeKm,
        Long fileNumber,
        Integer gpId
) {
}