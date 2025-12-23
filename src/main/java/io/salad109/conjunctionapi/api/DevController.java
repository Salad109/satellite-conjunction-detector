package io.salad109.conjunctionapi.api;

import io.salad109.conjunctionapi.conjunction.ConjunctionService;
import io.salad109.conjunctionapi.ingestion.IngestionService;
import io.salad109.conjunctionapi.ingestion.SyncResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DevController {

    private final IngestionService ingestionService;
    private final ConjunctionService conjunctionService;

    public DevController(IngestionService ingestionService, ConjunctionService conjunctionService) {
        this.ingestionService = ingestionService;
        this.conjunctionService = conjunctionService;
    }

    /**
     * Trigger a full catalog sync from Space-Track.
     */
    @PostMapping("/sync")
    public ResponseEntity<SyncResult> sync() {
        var result = ingestionService.sync();
        return result.successful() ?
                ResponseEntity.ok(result) :
                ResponseEntity.status(500).body(result);
    }


    @GetMapping("/scan")
    public ResponseEntity<List<Void>> scanForConjunctions() {
        conjunctionService.findConjunctions();
        return ResponseEntity.ok().build();
    }
}
