package io.salad109.conjunctiondetector.conjunction.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanLogRepository extends JpaRepository<ScanLog, Integer> {
    ScanLog findTopByOrderByStartedAtDesc();
}
