package io.salad109.conjunctiondetector.ui.internal;

import io.salad109.conjunctiondetector.conjunction.ConjunctionService;
import io.salad109.conjunctiondetector.ingestion.IngestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

    private final ConjunctionService conjunctionService;
    private final IngestionService ingestionService;

    public ScheduleService(ConjunctionService conjunctionService, IngestionService ingestionService) {
        this.conjunctionService = conjunctionService;
        this.ingestionService = ingestionService;
    }

    // @Scheduled(cron = "${conjunction.schedule.cron:0 21 */6 * * *}")
    @Transactional
    public void syncAndScan() {
        ingestionService.sync();
        conjunctionService.findConjunctions();
    }
}
