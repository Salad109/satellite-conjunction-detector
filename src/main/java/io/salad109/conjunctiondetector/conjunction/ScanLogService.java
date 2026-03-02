package io.salad109.conjunctiondetector.conjunction;

import io.salad109.conjunctiondetector.conjunction.internal.ScanLog;
import io.salad109.conjunctiondetector.conjunction.internal.ScanLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class ScanLogService {

    private final ScanLogRepository scanLogRepository;

    public ScanLogService(ScanLogRepository scanLogRepository) {
        this.scanLogRepository = scanLogRepository;
    }

    @Transactional(readOnly = true)
    public ScanResult getLatest() {
        ScanLog log = scanLogRepository.findTopByOrderByStartedAtDesc();
        if (log == null)
            return null;
        else
            return new ScanResult(
                    log.getStartedAt(),
                    log.getCompletedAt(),
                    log.getDurationMs(),
                    log.getSatellitesScanned(),
                    log.getConjunctionsDetected()
            );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void saveScanLog(OffsetDateTime startedAt, long durationMs, int satellitesScanned, int conjunctionsDetected) {
        scanLogRepository.save(new ScanLog(
                null,
                startedAt,
                OffsetDateTime.now(ZoneOffset.UTC),
                durationMs,
                satellitesScanned,
                conjunctionsDetected
        ));
    }
}
