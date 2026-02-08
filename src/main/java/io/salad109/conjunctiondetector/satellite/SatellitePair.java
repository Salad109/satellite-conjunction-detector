package io.salad109.conjunctiondetector.satellite;

import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import java.util.List;

public record SatellitePair(Satellite a, Satellite b) {

    public static List<Satellite> uniqueSatellites(List<SatellitePair> pairs, List<Satellite> allSatellites) {
        MutableIntSet ids = new IntHashSet(pairs.size() * 2);
        for (SatellitePair pair : pairs) {
            ids.add(pair.a().getNoradCatId());
            ids.add(pair.b().getNoradCatId());
        }
        return allSatellites.parallelStream()
                .filter(sat -> ids.contains(sat.getNoradCatId()))
                .toList();
    }
}
