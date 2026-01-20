package io.salad109.conjunctionapi.conjunction;

import io.salad109.conjunctionapi.conjunction.internal.*;
import io.salad109.conjunctionapi.satellite.PairReductionService;
import io.salad109.conjunctionapi.satellite.Satellite;
import io.salad109.conjunctionapi.satellite.SatellitePair;
import io.salad109.conjunctionapi.satellite.SatelliteService;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ConjunctionService {

    private static final Logger log = LoggerFactory.getLogger(ConjunctionService.class);

    private final SatelliteService satelliteService;
    private final ConjunctionRepository conjunctionRepository;
    private final PairReductionService pairReductionService;
    private final PropagationService propagationService;
    private final ScanService scanService;

    @Value("${conjunction.prepass-tolerance-km:12.5}")
    private double prepassToleranceKm;

    @Value("${conjunction.tolerance-km:375.0}")
    private double toleranceKm;

    @Value("${conjunction.collision-threshold-km:5.0}")
    private double thresholdKm;

    @Value("${conjunction.lookahead-hours:24}")
    private int lookaheadHours;

    @Value("${conjunction.step-seconds:32}")
    private int stepSeconds;

    @Value("${conjunction.interpolation-stride:1}")
    private int interpolationStride;

    public ConjunctionService(SatelliteService satelliteService,
                              ConjunctionRepository conjunctionRepository,
                              PairReductionService pairReductionService,
                              PropagationService propagationService,
                              ScanService scanService) {
        this.satelliteService = satelliteService;
        this.conjunctionRepository = conjunctionRepository;
        this.pairReductionService = pairReductionService;
        this.propagationService = propagationService;
        this.scanService = scanService;
    }

    @Transactional(readOnly = true)
    public Page<ConjunctionInfo> getConjunctions(Pageable pageable, boolean includeFormations) {
        if (includeFormations) {
            return conjunctionRepository.getConjunctionInfosWithFormations(pageable);
        } else {
            return conjunctionRepository.getConjunctionInfos(pageable);
        }
    }

    @Transactional
    public void findConjunctions() {
        StopWatch stopWatch = StopWatch.createStarted();
        log.info("Starting conjunction screening...");

        // Load satellites
        List<Satellite> satellites = satelliteService.getAll();
        log.debug("Loaded {} satellites", satellites.size());

        // Find and filter potential collision pairs
        List<SatellitePair> pairs = pairReductionService.findPotentialCollisionPairs(satellites, prepassToleranceKm);
        log.debug("Reduced to {} candidate pairs", pairs.size());

        // Extract unique satellites involved in any pair
        MutableIntSet satellitesInPairs = new IntHashSet(pairs.size() * 2);
        for (SatellitePair pair : pairs) {
            satellitesInPairs.add(pair.a().getNoradCatId());
            satellitesInPairs.add(pair.b().getNoradCatId());
        }

        // Filter to only satellites in candidate pairs
        List<Satellite> candidateSatellites = satellites.stream()
                .filter(sat -> satellitesInPairs.contains(sat.getNoradCatId()))
                .toList();

        log.debug("Filtered from {} to {} candidate satellites", satellites.size(), candidateSatellites.size());

        // Build propagators only for candidates
        Map<Integer, TLEPropagator> propagators = propagationService.buildPropagators(candidateSatellites);

        // Scan for conjunctions
        List<Conjunction> conjunctions = scanService.scanForConjunctions(pairs, propagators, toleranceKm, thresholdKm, lookaheadHours, stepSeconds, interpolationStride);

        // Save all conjunctions (upsert keeps closest per pair)
        if (!conjunctions.isEmpty()) {
            conjunctionRepository.batchUpsertIfCloser(conjunctions);
        }

        stopWatch.stop();
        log.info("Conjunction screening completed in {}ms, found {} conjunctions",
                stopWatch.getTime(), conjunctions.size());
    }
}
