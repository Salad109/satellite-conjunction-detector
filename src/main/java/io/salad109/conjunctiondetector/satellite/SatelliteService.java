package io.salad109.conjunctiondetector.satellite;

import io.salad109.conjunctiondetector.satellite.internal.SatelliteRepository;
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
    public List<Satellite> getAll() {
        return satelliteRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Map<Integer, Satellite> getByCatalogIds(Iterable<Integer> catalogIds) {
        return satelliteRepository.findAllById(catalogIds).stream()
                .collect(Collectors.toMap(Satellite::getNoradCatId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public SatelliteInfo getInfoByCatalogId(int catalogId) {
        return satelliteRepository.findSatelliteInfoByNoradCatId(catalogId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Satellite with NORAD ID " + catalogId + " not found"));
    }

    @Transactional
    public int save(List<Satellite> satellites) {
        return satelliteRepository.saveAll(satellites).size();
    }

    @Transactional
    public int deleteByCatalogIdsNotIn(Collection<Integer> catalogIds) {
        return satelliteRepository.deleteSatellitesByNoradCatIdNotIn(catalogIds);
    }
}
