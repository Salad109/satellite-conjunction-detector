package io.salad109.conjunctionapi.api;

import io.salad109.conjunctionapi.ingestion.IngestionLogService;
import io.salad109.conjunctionapi.ingestion.SyncResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final IngestionLogService ingestionLogService;

    public CatalogController(IngestionLogService ingestionLogService) {
        this.ingestionLogService = ingestionLogService;
    }

    @GetMapping("/sync/history")
    public ResponseEntity<Page<SyncResult>> getSyncHistory(Pageable pageable) {
        Page<SyncResult> history = ingestionLogService.getSyncHistory(pageable);
        return ResponseEntity.ok(history);
    }
}
