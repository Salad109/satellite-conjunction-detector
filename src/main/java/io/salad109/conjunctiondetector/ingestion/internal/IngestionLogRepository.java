package io.salad109.conjunctiondetector.ingestion.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionLogRepository extends JpaRepository<IngestionLog, Integer> {
    Page<IngestionLog> findAllByOrderByStartedAtDesc(Pageable pageable);

    IngestionLog findTopByOrderByStartedAtDesc();
}
