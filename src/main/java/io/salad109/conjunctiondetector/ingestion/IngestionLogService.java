package io.salad109.conjunctiondetector.ingestion;

import io.salad109.conjunctiondetector.ingestion.internal.IngestionLog;
import io.salad109.conjunctiondetector.ingestion.internal.IngestionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class IngestionLogService {

    private final IngestionLogRepository ingestionLogRepository;

    public IngestionLogService(IngestionLogRepository ingestionLogRepository) {
        this.ingestionLogRepository = ingestionLogRepository;
    }

    @Transactional(readOnly = true)
    public SyncResult getLatest() {
        IngestionLog log = ingestionLogRepository.findTopByOrderByStartedAtDesc();
        if (log == null)
            return null;
        else
            return new SyncResult(
                    log.getStartedAt(),
                    log.getObjectsInserted(),
                    log.getObjectsUpdated(),
                    log.getObjectsUnchanged(),
                    log.getObjectsSkipped(),
                    log.getObjectsDeleted(),
                    log.isSuccessful()
            );
    }

    /**
     * Save an ingestion log entry in a new transaction. REQUIRES_NEW ensures log isn't rolled back if sync fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void saveIngestionLog(SyncResult syncResult, String errorMessage) {
        ingestionLogRepository.save(new IngestionLog(
                null,
                syncResult.startedAt(),
                OffsetDateTime.now(ZoneOffset.UTC),
                syncResult.objectsInserted(),
                syncResult.objectsUpdated(),
                syncResult.objectsUnchanged(),
                syncResult.objectsSkipped(),
                syncResult.objectsDeleted(),
                syncResult.successful(),
                errorMessage
        ));
    }
}
