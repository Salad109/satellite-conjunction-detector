package io.salad109.conjunctiondetector.conjunction.internal;

import io.salad109.conjunctiondetector.satellite.SatelliteScanInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

import java.io.File;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class IridiumCosmosBackTest {

    // Last TLEs before collision, taken from Space-Track GP history
    // Iridium 33 (NORAD ID 24946)
    private static final String IRIDIUM_TLE1 = "1 24946U 97051C   09040.78448243 +.00000153 +00000-0 +47668-4 0  9994";
    private static final String IRIDIUM_TLE2 = "2 24946 086.3994 121.7028 0002288 085.1644 274.9812 14.34219863597336";

    // Cosmos 2251 (NORAD ID 22675)
    private static final String COSMOS_TLE1 = "1 22675U 93036A   09040.49834364 -.00000001  00000-0  95251-5 0  9996";
    private static final String COSMOS_TLE2 = "2 22675 074.0355 019.4646 0016027 098.7014 261.5952 14.31135643817415";

    // Known collision time: Feb 10, 2009 16:56 UTC
    private static final OffsetDateTime COLLISION_TIME =
            OffsetDateTime.of(2009, 2, 10, 16, 55, 59, 806_000_000, ZoneOffset.UTC);

    private final PropagationService propagationService = new PropagationService();
    private final ScanService scanService = new ScanService(propagationService);
    private final CollisionProbabilityService probabilityService = new CollisionProbabilityService();

    @BeforeAll
    static void initOrekit() {
        File orekitData = new File("src/main/resources/orekit-data");
        if (orekitData.exists()) {
            DataContext.getDefault().getDataProvidersManager()
                    .addProvider(new DirectoryCrawler(orekitData));
        }
    }

    @Test
    void sgp4FindsCloseApproachAtCollisionTime() {

        TLEPropagator iridiumProp = TLEPropagator.selectExtrapolator(new TLE(IRIDIUM_TLE1, IRIDIUM_TLE2));
        TLEPropagator cosmosProp = TLEPropagator.selectExtrapolator(new TLE(COSMOS_TLE1, COSMOS_TLE2));

        AbsoluteDate collisionDate = new AbsoluteDate(COLLISION_TIME.toInstant(), TimeScalesFactory.getUTC());

        PVCoordinates pvIridium = iridiumProp.getPVCoordinates(collisionDate, iridiumProp.getFrame());
        PVCoordinates pvCosmos = cosmosProp.getPVCoordinates(collisionDate, iridiumProp.getFrame());

        double distanceM = pvIridium.getPosition().subtract(pvCosmos.getPosition()).getNorm();
        double relVelMS = pvIridium.getVelocity().subtract(pvCosmos.getVelocity()).getNorm();

        System.out.printf("Distance: %.1f m%n", distanceM);
        System.out.printf("Relative velocity: %.1f m/s (expected ~11700 m/s)%n", relVelMS);

        assertThat(distanceM)
                .as("SGP4 miss distance at known collision time")
                .isLessThan(5000);
        assertThat(relVelMS)
                .as("relative velocity (expected ~11700 m/s)")
                .isCloseTo(11700, offset(1000.0));
    }

    @Test
    void fullPipelineDetectsCollision() {

        double toleranceKm = 72.0;
        double cellSizeKm = 48.0;
        double stepSeconds = 9;
        int interpolationStride = 50;
        int lookaheadHours = 2;
        double thresholdKm = 5.0;

        OffsetDateTime iridiumEpoch = OffsetDateTime.of(2009, 2, 9, 18, 49, 39, 0, ZoneOffset.UTC);
        OffsetDateTime cosmosEpoch = OffsetDateTime.of(2009, 2, 9, 11, 57, 36, 0, ZoneOffset.UTC);

        SatelliteScanInfo iridium = new SatelliteScanInfo(24946, IRIDIUM_TLE1, IRIDIUM_TLE2,
                iridiumEpoch, 780.0, "PAYLOAD");
        SatelliteScanInfo cosmos = new SatelliteScanInfo(22675, COSMOS_TLE1, COSMOS_TLE2,
                cosmosEpoch, 780.0, "PAYLOAD");

        List<SatelliteScanInfo> satellites = List.of(iridium, cosmos);
        Map<Integer, TLEPropagator> propagators = propagationService.buildPropagators(satellites);

        // Propagate and interpolate
        PropagationService.KnotCache knots = propagationService.computeKnots(
                propagators, COLLISION_TIME.minusHours(1), stepSeconds, lookaheadHours, interpolationStride);
        PropagationService.PositionCache cache = propagationService.interpolate(knots);

        // Coarse spatial scan
        List<ScanService.CoarseDetection> detections = scanService.checkPairs(
                satellites, cache, toleranceKm, cellSizeKm);

        assertThat(detections).as("coarse detections").isNotEmpty();

        // Group and reduce
        List<ScanService.CoarseDetection> events = scanService.groupAndReduce(detections);

        assertThat(events).as("grouped events").isNotEmpty();

        // Refine
        List<ScanService.RefinedEvent> refined = events.stream()
                .map(e -> scanService.refineDetection(e, cache, propagators, stepSeconds, thresholdKm))
                .filter(Objects::nonNull)
                .toList();

        assertThat(refined).as("refined events").isNotEmpty();

        ScanService.RefinedEvent best = refined.stream()
                .min(Comparator.comparingDouble(ScanService.RefinedEvent::distanceKm))
                .orElseThrow();

        Conjunction conjunction = probabilityService.computeProbabilityAndBuild(best);

        long tcaErrorMs = Duration.between(COLLISION_TIME, conjunction.getTca()).toMillis();

        System.out.printf("Closest approach: %.3f km%n", conjunction.getMissDistanceKm());
        System.out.printf("TCA: %s (error: %d ms)%n", conjunction.getTca(), tcaErrorMs);
        System.out.printf("Relative velocity: %.1f m/s (expected ~11700 m/s)%n", conjunction.getRelativeVelocityMS());
        System.out.printf("Collision probability: %.6e%n", conjunction.getCollisionProbability());

        assertThat(conjunction.getMissDistanceKm())
                .as("pipeline miss distance")
                .isLessThan(5.0);
        assertThat(Math.abs(tcaErrorMs / 1000))
                .as("TCA error from known collision time")
                .isLessThan(30);
        assertThat(conjunction.getRelativeVelocityMS())
                .as("relative velocity (expected ~11700 m/s)")
                .isCloseTo(11700, offset(1000.0));
        assertThat(conjunction.getObject1NoradId())
                .as("ordering: lower NORAD ID first")
                .isEqualTo(22675);
        assertThat(conjunction.getObject2NoradId())
                .isEqualTo(24946);
    }
}
