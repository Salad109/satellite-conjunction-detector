package io.salad109.conjunctiondetector.satellite;

import io.salad109.conjunctiondetector.satellite.internal.SatelliteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SatelliteService {

    private final SatelliteRepository satelliteRepository;

    public SatelliteService(SatelliteRepository satelliteRepository) {
        this.satelliteRepository = satelliteRepository;
    }

    @Transactional(readOnly = true)
    public List<SatelliteScanInfo> getAllScanInfo() {
        return satelliteRepository.findAllSatelliteScanInfo();
    }

    @Transactional(readOnly = true)
    public long count() {
        return satelliteRepository.count();
    }

    @Transactional(readOnly = true)
    public CatalogBreakdown getCatalogBreakdown(int topNameTokenLimit) {
        List<NameTokenCount> topNameTokens = satelliteRepository.findTopNameTokens(topNameTokenLimit);
        return new CatalogBreakdown(
                satelliteRepository.countByObjectType("PAYLOAD"),
                satelliteRepository.countByObjectType("DEBRIS"),
                satelliteRepository.countByObjectType("ROCKET BODY"),
                satelliteRepository.countByObjectType("UNKNOWN")
                        + satelliteRepository.countByObjectType("TBA"),
                satelliteRepository.countLeo(),
                satelliteRepository.countMeo(),
                satelliteRepository.countGeo(),
                topNameTokens
        );
    }

    @Transactional(readOnly = true)
    public Map<Integer, Satellite> getByCatalogIds(Iterable<Integer> catalogIds) {
        return satelliteRepository.findAllById(catalogIds).stream()
                .collect(Collectors.toMap(Satellite::getNoradCatId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public SatelliteDetails getDetailsByCatalogId(int catalogId) {
        return satelliteRepository.findSatelliteDetailsByNoradCatId(catalogId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Satellite with NORAD ID " + catalogId + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<SatelliteBriefInfo> getBriefInfos(Pageable pageable) {
        return satelliteRepository.getSatelliteBriefInfos(pageable);
    }

    @Transactional
    public void updateConjunctionCounts() {
        satelliteRepository.updateConjunctionCounts();
    }

    @Transactional
    public int save(List<Satellite> satellites) {
        return satelliteRepository.saveAll(satellites).size();
    }

    @Transactional
    public int deleteByCatalogIdsNotIn(Collection<Integer> catalogIds) {
        return satelliteRepository.deleteSatellitesByNoradCatIdNotIn(catalogIds);
    }

    public record CatalogBreakdown(
            long payloadCount,
            long debrisCount,
            long rocketBodyCount,
            long unknownCount,
            long leoCount,
            long meoCount,
            long geoCount,
            List<NameTokenCount> topNameTokens
    ) {
        public CatalogBreakdown {
            topNameTokens = List.copyOf(topNameTokens);
        }
    }

    public record NameTokenCount(String fragment, long count) {
    }
}
