package io.salad109.conjunctiondetector.ui.internal;

import io.salad109.conjunctiondetector.conjunction.ConjunctionService;
import io.salad109.conjunctiondetector.conjunction.ScanLogService;
import io.salad109.conjunctiondetector.ingestion.IngestionLogService;
import io.salad109.conjunctiondetector.satellite.SatelliteService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;

@Controller
public class UiController {

    private final ConjunctionService conjunctionService;
    private final IngestionLogService ingestionLogService;
    private final ScanLogService scanLogService;
    private final SatelliteService satelliteService;

    public UiController(ConjunctionService conjunctionService, IngestionLogService ingestionLogService,
                        ScanLogService scanLogService, SatelliteService satelliteService) {
        this.conjunctionService = conjunctionService;
        this.ingestionLogService = ingestionLogService;
        this.scanLogService = scanLogService;
        this.satelliteService = satelliteService;
    }

    @GetMapping
    public String index() {
        return "index";
    }

    @GetMapping("/conjunctions/{id}")
    public String conjunction(@PathVariable Long id, Model model) {
        model.addAttribute("visualization", conjunctionService.getVisualizationData(id));
        return "visualization";
    }

    @GetMapping("/satellites/{noradId}")
    public String satellite(@PathVariable int noradId, Model model) {
        model.addAttribute("satellite", satelliteService.getDetailsByCatalogId(noradId));
        model.addAttribute("conjunctions", conjunctionService.getConjunctionInfosByNoradId(noradId));
        return "satellite";
    }

    @GetMapping("/catalog")
    public String catalog() {
        return "catalog";
    }

    @GetMapping("/hx/satellites")
    public String satellitesFragment(@PageableDefault(sort = "noradCatId", direction = Sort.Direction.ASC) Pageable pageable, Model model) {
        model.addAttribute("page", satelliteService.getBriefInfos(pageable));

        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        model.addAttribute("sortField", order != null ? order.getProperty() : "noradCatId");
        model.addAttribute("sortDir", order != null ? order.getDirection().name().toLowerCase(Locale.ROOT) : "asc");

        return "fragments/satellite-table";
    }

    @GetMapping("/hx/conjunctions")
    public String conjunctionsFragment(@PageableDefault(sort = "tca", direction = Sort.Direction.DESC) Pageable pageable,
                                       @RequestParam(defaultValue = "false") boolean includeFormations,
                                       Model model) {
        model.addAttribute("page", conjunctionService.getConjunctions(pageable, includeFormations));
        model.addAttribute("includeFormations", includeFormations);

        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        model.addAttribute("sortField", order != null ? order.getProperty() : "tca");
        model.addAttribute("sortDir", order != null ? order.getDirection().name().toLowerCase(Locale.ROOT) : "desc");

        return "fragments/conjunction-table";
    }

    @GetMapping("/hx/lastsync")
    public String lastSyncFragment(Model model) {
        model.addAttribute("log", ingestionLogService.getLatest());
        return "fragments/last-sync-log";
    }

    @GetMapping("/hx/lastscan")
    public String lastScanFragment(Model model) {
        model.addAttribute("scan", scanLogService.getLatest());
        return "fragments/last-scan-log";
    }
}
