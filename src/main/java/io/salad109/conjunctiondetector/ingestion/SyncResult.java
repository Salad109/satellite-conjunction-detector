package io.salad109.conjunctiondetector.ingestion;

import java.time.OffsetDateTime;

public record SyncResult(
        OffsetDateTime startedAt,
        int objectsInserted,
        int objectsUpdated,
        int objectsUnchanged,
        int objectsSkipped,
        int objectsDeleted,
        boolean successful
) {
}