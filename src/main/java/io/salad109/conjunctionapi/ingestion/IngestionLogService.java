package io.salad109.conjunctionapi.ingestion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class IngestionLogService {

    private final IngestionLogRepository ingestionLogRepository;

    public IngestionLogService(IngestionLogRepository ingestionLogRepository) {
        this.ingestionLogRepository = ingestionLogRepository;
    }

    /**
     * Save an ingestion log entry in a new transaction. REQUIRES_NEW ensures log isn't rolled back if sync fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveIngestionLog(OffsetDateTime startedAt, IngestionResult ingestionResult, boolean successful, String errorMessage) {
        ingestionLogRepository.save(new IngestionLog(
                null,
                startedAt,
                OffsetDateTime.now(),
                ingestionResult.processed(),
                ingestionResult.created(),
                ingestionResult.updated(),
                ingestionResult.skipped(),
                ingestionResult.deleted(),
                successful,
                errorMessage
        ));
    }
}
