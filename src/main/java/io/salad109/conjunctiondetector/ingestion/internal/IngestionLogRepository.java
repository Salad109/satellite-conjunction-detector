package io.salad109.conjunctiondetector.ingestion.internal;

import io.salad109.conjunctiondetector.ingestion.SyncResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngestionLogRepository extends JpaRepository<IngestionLog, Integer> {

    @Query("SELECT new io.salad109.conjunctiondetector.ingestion.SyncResult(" +
            "i.startedAt, i.objectsInserted, i.objectsUpdated, i.objectsUnchanged, " +
            "i.objectsSkipped, i.objectsDeleted, i.successful) " +
            "FROM IngestionLog i ORDER BY i.startedAt DESC")
    List<SyncResult> findRecent(Pageable pageable);
}
