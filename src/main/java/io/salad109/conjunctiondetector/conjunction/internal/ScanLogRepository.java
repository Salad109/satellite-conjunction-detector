package io.salad109.conjunctiondetector.conjunction.internal;

import io.salad109.conjunctiondetector.conjunction.ScanResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ScanLogRepository extends JpaRepository<ScanLog, Integer> {

    @Query("SELECT new io.salad109.conjunctiondetector.conjunction.ScanResult(" +
            "s.startedAt, s.completedAt, s.durationMs, s.satellitesScanned, s.conjunctionsDetected) " +
            "FROM ScanLog s ORDER BY s.startedAt DESC")
    List<ScanResult> findRecent(Pageable pageable);
}
