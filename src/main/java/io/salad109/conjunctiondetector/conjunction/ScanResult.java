package io.salad109.conjunctiondetector.conjunction;

import java.time.OffsetDateTime;

public record ScanResult(OffsetDateTime startedAt,
                         OffsetDateTime completedAt,
                         long durationMs,
                         int satellitesScanned,
                         int conjunctionsDetected) {
}
