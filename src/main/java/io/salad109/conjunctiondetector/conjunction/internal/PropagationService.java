package io.salad109.conjunctiondetector.conjunction.internal;

import io.salad109.conjunctiondetector.satellite.Satellite;
import io.salad109.conjunctiondetector.satellite.SatellitePair;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.orekit.frames.Frame;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class PropagationService {

    private static final Logger log = LoggerFactory.getLogger(PropagationService.class);

    public Map<Integer, TLEPropagator> buildPropagators(List<Satellite> satellites) {
        Map<Integer, TLEPropagator> propagators = new HashMap<>();

        for (Satellite sat : satellites) {
            TLE tle = new TLE(sat.getTleLine1(), sat.getTleLine2());
            propagators.put(sat.getNoradCatId(), TLEPropagator.selectExtrapolator(tle));
        }

        return propagators;
    }

    /**
     * Propagate both satellites to a given time and return distance, relative velocity, and PV coordinates.
     */
    MeasurementResult propagateAndMeasure(SatellitePair pair, Map<Integer, TLEPropagator> propagators,
                                          OffsetDateTime time, double thresholdKm) {
        AbsoluteDate date = toAbsoluteDate(time);

        try {
            TLEPropagator propA = propagators.get(pair.a().getNoradCatId());
            TLEPropagator propB = propagators.get(pair.b().getNoradCatId());

            PVCoordinates pvA, pvB;
            Frame frame;

            synchronized (propA) {
                frame = propA.getFrame();
                pvA = propA.getPVCoordinates(date, frame);
            }
            synchronized (propB) {
                pvB = propB.getPVCoordinates(date, frame);
            }

            double distance = calculateDistance(pvA, pvB);
            double velocity = distance <= thresholdKm ? calculateRelativeVelocity(pvA, pvB) : 0.0;

            return new MeasurementResult(distance, velocity, pvA, pvB, frame, date);
        } catch (Exception e) {
            log.warn("Failed to propagate for refinement: {}", e.getMessage());
            return new MeasurementResult(Double.MAX_VALUE, 0.0, null, null, null, null);
        }
    }

    /**
     * Propagate a single satellite to a given time and return position in km as [x, y, z].
     */
    double[] propagateToPositionKm(Satellite sat, Map<Integer, TLEPropagator> propagators, OffsetDateTime time) {
        AbsoluteDate date = toAbsoluteDate(time);
        try {
            TLEPropagator prop = propagators.get(sat.getNoradCatId());
            PVCoordinates pv;

            synchronized (prop) {
                pv = prop.getPVCoordinates(date, prop.getFrame());
            }

            return new double[]{
                    pv.getPosition().getX() / 1000.0,
                    pv.getPosition().getY() / 1000.0,
                    pv.getPosition().getZ() / 1000.0
            };
        } catch (Exception e) {
            log.warn("Failed to propagate satellite {}: {}", sat.getNoradCatId(), e.getMessage());
            return new double[]{0, 0, 0};
        }
    }

    /**
     * Pre-compute positions for all satellites across all time steps.
     */
    public PositionCache precomputePositions(Map<Integer, TLEPropagator> propagators, OffsetDateTime startTime,
                                             int stepSeconds, int lookaheadHours, int interpolationStride) {
        int totalSteps = (lookaheadHours * 3600) / stepSeconds + 1;
        int stride = Math.max(1, interpolationStride);

        OffsetDateTime[] times = new OffsetDateTime[totalSteps];
        for (int i = 0; i < totalSteps; i++) {
            times[i] = startTime.plusSeconds((long) i * stepSeconds);
        }

        Integer[] satIds = propagators.keySet().toArray(Integer[]::new);
        MutableIntIntMap noradIdToArrayId = new IntIntHashMap(satIds.length);
        int[] arrayIdToNoradId = new int[satIds.length];
        for (int i = 0; i < satIds.length; i++) {
            noradIdToArrayId.put(satIds[i], i);
            arrayIdToNoradId[i] = satIds[i];
        }

        int numSats = satIds.length;
        double[][] x = new double[numSats][totalSteps];
        double[][] y = new double[numSats][totalSteps];
        double[][] z = new double[numSats][totalSteps];
        boolean[][] valid = new boolean[numSats][totalSteps];

        IntStream.range(0, numSats).parallel().forEach(s -> {
            TLEPropagator prop = propagators.get(satIds[s]);

            // SGP4 at stride points
            for (int step = 0; step < totalSteps; step += stride) {
                try {
                    PVCoordinates pv = prop.getPVCoordinates(toAbsoluteDate(times[step]), prop.getFrame());
                    x[s][step] = pv.getPosition().getX() / 1000.0;
                    y[s][step] = pv.getPosition().getY() / 1000.0;
                    z[s][step] = pv.getPosition().getZ() / 1000.0;
                    valid[s][step] = true;
                } catch (Exception e) {
                    valid[s][step] = false;
                }
            }

            // Linear interpolation between strides
            for (int a = 0; a + stride < totalSteps; a += stride) {
                int b = a + stride;
                if (!valid[s][a] || !valid[s][b]) continue;
                for (int step = a + 1; step < b; step++) {
                    double t = (double) (step - a) / stride;
                    x[s][step] = x[s][a] + t * (x[s][b] - x[s][a]);
                    y[s][step] = y[s][a] + t * (y[s][b] - y[s][a]);
                    z[s][step] = z[s][a] + t * (z[s][b] - z[s][a]);
                    valid[s][step] = true;
                }
            }
        });

        return new PositionCache(noradIdToArrayId, arrayIdToNoradId, times, x, y, z, valid);
    }

    /**
     * Calculate distance in kilometers between two PVCoordinates.
     */
    private double calculateDistance(PVCoordinates pvA, PVCoordinates pvB) {
        double dx = (pvA.getPosition().getX() - pvB.getPosition().getX()) / 1000.0;
        double dy = (pvA.getPosition().getY() - pvB.getPosition().getY()) / 1000.0;
        double dz = (pvA.getPosition().getZ() - pvB.getPosition().getZ()) / 1000.0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calculate relative velocity in kilometers per second between two PVCoordinates.
     */
    private double calculateRelativeVelocity(PVCoordinates pvA, PVCoordinates pvB) {
        double dvx = pvA.getVelocity().getX() - pvB.getVelocity().getX();
        double dvy = pvA.getVelocity().getY() - pvB.getVelocity().getY();
        double dvz = pvA.getVelocity().getZ() - pvB.getVelocity().getZ();
        return Math.sqrt(dvx * dvx + dvy * dvy + dvz * dvz);
    }

    private AbsoluteDate toAbsoluteDate(OffsetDateTime dateTime) {
        return new AbsoluteDate(
                dateTime.getYear(),
                dateTime.getMonthValue(),
                dateTime.getDayOfMonth(),
                dateTime.getHour(),
                dateTime.getMinute(),
                dateTime.getSecond() + dateTime.getNano() / 1e9,
                TimeScalesFactory.getUTC()
        );
    }

    public record PositionCache(MutableIntIntMap noradIdToArrayId, int[] arrayIdToNoradId, OffsetDateTime[] times,
                                double[][] x, double[][] y, double[][] z, boolean[][] valid) {
        double distanceSquaredAt(int a, int b, int step) {
            double dx = x[a][step] - x[b][step];
            double dy = y[a][step] - y[b][step];
            double dz = z[a][step] - z[b][step];
            return dx * dx + dy * dy + dz * dz;
        }
    }

    record MeasurementResult(double distanceKm, double velocityKmS, PVCoordinates pvA, PVCoordinates pvB, Frame frame,
                             AbsoluteDate absoluteDate) {
    }
}
