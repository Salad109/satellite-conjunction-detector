package io.salad109.conjunctiondetector.conjunction;

import io.salad109.conjunctiondetector.conjunction.internal.*;
import io.salad109.conjunctiondetector.satellite.SatelliteScanInfo;
import io.salad109.conjunctiondetector.satellite.SatelliteService;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.time.StopWatch;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ConjunctionService {

    private static final Logger log = LoggerFactory.getLogger(ConjunctionService.class);

    private final SatelliteService satelliteService;
    private final ConjunctionRepository conjunctionRepository;
    private final PropagationService propagationService;
    private final ScanService scanService;
    private final CollisionProbabilityService collisionProbabilityService;

    @Value("${conjunction.tolerance-km:72.0}")
    private double toleranceKm;

    @Value("${conjunction.cell-size-km:46.875}")
    private double cellSizeKm;

    @Value("${conjunction.collision-threshold-km:5.0}")
    private double thresholdKm;

    @Value("${conjunction.lookahead-hours:24}")
    private int lookaheadHours;

    @Value("${conjunction.step-seconds:9.375}")
    private double stepSeconds;

    @Value("${conjunction.interpolation-stride:130}")
    private int interpolationStride;

    public ConjunctionService(SatelliteService satelliteService,
                              ConjunctionRepository conjunctionRepository,
                              PropagationService propagationService,
                              ScanService scanService,
                              CollisionProbabilityService collisionProbabilityService) {
        this.satelliteService = satelliteService;
        this.conjunctionRepository = conjunctionRepository;
        this.propagationService = propagationService;
        this.scanService = scanService;
        this.collisionProbabilityService = collisionProbabilityService;
    }

    @PostConstruct
    void validateProperties() {
        if (toleranceKm <= 0) throw new IllegalStateException("conjunction.tolerance-km must be positive");
        if (cellSizeKm <= 0) throw new IllegalStateException("conjunction.cell-size-km must be positive");
        if (thresholdKm <= 0) throw new IllegalStateException("conjunction.collision-threshold-km must be positive");
        if (lookaheadHours <= 0) throw new IllegalStateException("conjunction.lookahead-hours must be positive");
        if (stepSeconds <= 0) throw new IllegalStateException("conjunction.step-seconds must be positive");
        if (interpolationStride <= 0)
            throw new IllegalStateException("conjunction.interpolation-stride must be positive");
    }

    @Transactional(readOnly = true)
    public Page<ConjunctionInfo> getConjunctions(Pageable pageable, boolean includeFormations) {
        if (includeFormations) {
            return conjunctionRepository.getConjunctionInfosWithFormations(pageable);
        } else {
            return conjunctionRepository.getConjunctionInfos(pageable);
        }
    }

    @Transactional(readOnly = true)
    public VisualizationData getVisualizationData(Long id) {
        return conjunctionRepository.getVisualizationData(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunction with ID " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<ConjunctionInfo> getConjunctionInfosByNoradId(int id) {
        return conjunctionRepository.getConjunctionInfosByNoradId(id);
    }

    @Transactional
    public void findConjunctions() {
        StopWatch stopWatch = StopWatch.createStarted();
        log.info("Starting conjunction screening...");

        OffsetDateTime startTime = OffsetDateTime.now(ZoneOffset.UTC);

        // Load satellites
        List<SatelliteScanInfo> satellites = satelliteService.getAllScanInfo();
        log.debug("Loaded {} satellites", satellites.size());

        // Build propagators
        Map<Integer, TLEPropagator> propagators = propagationService.buildPropagators(satellites);

        // SGP4 at stride points
        PropagationService.KnotCache knots = propagationService.computeKnots(
                propagators, startTime, stepSeconds, lookaheadHours, interpolationStride);

        // Interpolate to full position cache
        PropagationService.PositionCache positionCache = propagationService.interpolate(knots);

        // Coarse sweep
        List<ScanService.CoarseDetection> detections = scanService.checkPairs(satellites, positionCache, toleranceKm, cellSizeKm);
        log.debug("Coarse sweep found {} detections", detections.size());

        // Sort, cluster, reduce to best-per-event
        List<ScanService.CoarseDetection> events = scanService.groupAndReduce(detections);
        log.debug("Grouped into {} events", events.size());

        // Refine
        List<ScanService.RefinedEvent> refined = events.parallelStream()
                .map(det -> scanService.refineDetection(det, positionCache, propagators, stepSeconds, thresholdKm))
                .filter(Objects::nonNull)
                .toList();

        // Collision probability
        List<Conjunction> conjunctions = refined.parallelStream()
                .map(collisionProbabilityService::computeProbabilityAndBuild)
                .toList();

        // Persist
        conjunctionRepository.truncate();
        conjunctionRepository.saveAll(conjunctions);

        stopWatch.stop();
        log.info("Conjunction screening completed in {}ms, found {} conjunctions",
                stopWatch.getTime(), conjunctions.size());
    }
}
