package io.salad109.conjunctiondetector.satellite;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

@Service
public class PairReductionService {

    /**
     * Finds all pairs of satellites that could potentially collide.
     * Uses orbital geometry filters to reduce the number of pairs for detailed analysis.
     */
    public List<SatellitePair> findPotentialCollisionPairs(List<Satellite> satellites, double toleranceKm) {
        int n = satellites.size();

        // Pre-extract fields into arrays
        double[] perigees = new double[n];
        double[] apogees = new double[n];
        double[] inclinations = new double[n];
        double[] raans = new double[n];
        double[] argPerigees = new double[n];
        double[] eccentricities = new double[n];
        double[] semiMajorAxes = new double[n];
        boolean[] isDebris = new boolean[n];

        for (int i = 0; i < n; i++) {
            Satellite s = satellites.get(i);
            perigees[i] = s.getPerigeeKm();
            apogees[i] = s.getApogeeKm();
            inclinations[i] = Math.toRadians(s.getInclination());
            raans[i] = Math.toRadians(s.getRaan());
            argPerigees[i] = Math.toRadians(s.getArgPerigee());
            eccentricities[i] = s.getEccentricity();
            semiMajorAxes[i] = s.getSemiMajorAxisKm();
            isDebris[i] = "DEBRIS".equals(s.getObjectType());
        }

        return IntStream.range(0, n)
                .parallel()
                .boxed()
                .mapMulti((Integer i, Consumer<SatellitePair> consumer) -> {
                    double perigeeA = perigees[i];
                    double apogeeA = apogees[i];
                    boolean debrisA = isDebris[i];
                    double iA = inclinations[i];
                    double raanA = raans[i];
                    double omegaA = argPerigees[i];
                    double eA = eccentricities[i];
                    double aA = semiMajorAxes[i];

                    for (int j = i + 1; j < n; j++) {
                        if (altitudeShellsMiss(perigeeA, apogeeA, perigees[j], apogees[j], toleranceKm)
                                || bothDebris(debrisA, isDebris[j])
                                || orbitalPlanesMiss(iA, raanA, omegaA, eA, aA,
                                inclinations[j], raans[j], argPerigees[j],
                                eccentricities[j], semiMajorAxes[j], toleranceKm)) {
                            continue;
                        }

                        consumer.accept(new SatellitePair(satellites.get(i), satellites.get(j)));
                    }
                })
                .toList();
    }

    public boolean altitudeShellsMiss(double perigeeA, double apogeeA,
                                      double perigeeB, double apogeeB, double toleranceKm) {
        return apogeeA + toleranceKm < perigeeB || apogeeB + toleranceKm < perigeeA;
    }

    public boolean bothDebris(boolean debrisA, boolean debrisB) {
        return debrisA && debrisB;
    }

    public boolean orbitalPlanesMiss(
            double iA, double raanA, double omegaA, double eA, double aA,
            double iB, double raanB, double omegaB, double eB, double aB,
            double toleranceKm) {

        double deltaRaan = raanA - raanB;

        // Coplanar orbits can intersect anywhere
        double relativeInclination = computeRelativeInclination(iA, iB, deltaRaan);
        if (relativeInclination < Math.toRadians(0.1)) {
            return false;
        }

        // Find where each orbit crosses the intersection line between orbital planes
        double alphaA = computeAlphaA(iA, iB, deltaRaan);
        double alphaB = computeAlphaB(iA, iB, deltaRaan);

        // Convert to true anomaly
        double nuA1 = normalizeAngle(alphaA - omegaA);
        double nuA2 = normalizeAngle(alphaA + Math.PI - omegaA);
        double nuB1 = normalizeAngle(alphaB - omegaB);
        double nuB2 = normalizeAngle(alphaB + Math.PI - omegaB);

        // Compute orbital radius at crossing points
        double rA1 = orbitalRadius(aA, eA, nuA1);
        double rA2 = orbitalRadius(aA, eA, nuA2);
        double rB1 = orbitalRadius(aB, eB, nuB1);
        double rB2 = orbitalRadius(aB, eB, nuB2);

        // Check all four combinations
        double diff1 = Math.abs(rA1 - rB1);
        double diff2 = Math.abs(rA1 - rB2);
        double diff3 = Math.abs(rA2 - rB1);
        double diff4 = Math.abs(rA2 - rB2);

        double minDiff = Math.min(Math.min(diff1, diff2), Math.min(diff3, diff4));
        return minDiff > toleranceKm;
    }

    private double computeRelativeInclination(double iA, double iB, double deltaRaan) {
        double cosRelInc = Math.cos(iA) * Math.cos(iB)
                + Math.sin(iA) * Math.sin(iB) * Math.cos(deltaRaan);
        return Math.acos(Math.clamp(cosRelInc, -1, 1));
    }

    private double computeAlphaA(double iA, double iB, double deltaRaan) {
        double sinIB = Math.sin(iB);
        double cosIB = Math.cos(iB);
        double sinIA = Math.sin(iA);
        double cosIA = Math.cos(iA);
        double sinDeltaRaan = Math.sin(deltaRaan);
        double cosDeltaRaan = Math.cos(deltaRaan);

        double y = sinIB * sinDeltaRaan;
        double x = sinIA * cosIB - cosIA * sinIB * cosDeltaRaan;

        return Math.atan2(y, x);
    }

    private double computeAlphaB(double iA, double iB, double deltaRaan) {
        double sinIB = Math.sin(iB);
        double cosIB = Math.cos(iB);
        double sinIA = Math.sin(iA);
        double cosIA = Math.cos(iA);
        double sinDeltaRaan = Math.sin(deltaRaan);
        double cosDeltaRaan = Math.cos(deltaRaan);

        double y = -sinIA * sinDeltaRaan;
        double x = sinIB * cosIA - cosIB * sinIA * cosDeltaRaan;

        return Math.atan2(y, x);
    }

    private double orbitalRadius(double semiMajorAxis, double eccentricity, double trueAnomaly) {
        double p = semiMajorAxis * (1 - eccentricity * eccentricity);
        return p / (1 + eccentricity * Math.cos(trueAnomaly));
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }
}
