package io.salad109.conjunctiondetector.conjunction;

import io.salad109.conjunctiondetector.conjunction.internal.ScanLog;
import io.salad109.conjunctiondetector.conjunction.internal.ScanLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ScanLogService {

    private final ScanLogRepository scanLogRepository;

    public ScanLogService(ScanLogRepository scanLogRepository) {
        this.scanLogRepository = scanLogRepository;
    }

    @Transactional(readOnly = true)
    public List<ScanResult> getRecent(int n) {
        return scanLogRepository.findRecent(PageRequest.of(0, n));
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
