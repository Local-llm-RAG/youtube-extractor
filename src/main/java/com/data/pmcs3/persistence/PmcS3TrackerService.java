package com.data.pmcs3.persistence;

import com.data.pmcs3.persistence.entity.PmcS3Tracker;
import com.data.pmcs3.persistence.entity.PmcS3TrackerStatus;
import com.data.pmcs3.persistence.repository.PmcS3TrackerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Read/write facade for {@link PmcS3Tracker}. All progress updates go through
 * atomic DB statements so they are safe under concurrent virtual-thread execution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmcS3TrackerService {

    private final PmcS3TrackerRepository repository;

    /**
     * Returns an in-progress tracker for the given batchId (resuming a prior run)
     * or creates and persists a new one.
     */
    @Transactional
    public PmcS3Tracker getOrCreate(String batchId) {
        Optional<PmcS3Tracker> existing = repository.findByBatchId(batchId);
        if (existing.isPresent()) {
            log.info("Resuming PMC S3 tracker batchId={} id={} processed={}/{}",
                    batchId, existing.get().getId(),
                    existing.get().getTotalProcessed(), existing.get().getTotalDiscovered());
            return existing.get();
        }
        PmcS3Tracker tracker = PmcS3Tracker.builder()
                .batchId(batchId)
                .startedAt(OffsetDateTime.now())
                .totalDiscovered(0)
                .totalProcessed(0)
                .totalSkipped(0)
                .status(PmcS3TrackerStatus.RUNNING)
                .build();
        return repository.save(tracker);
    }

    @Transactional
    public void updateDiscovered(Long id, int count) {
        repository.updateDiscovered(id, count);
    }

    @Transactional
    public void markCompleted(Long id) {
        repository.markStatus(id, PmcS3TrackerStatus.COMPLETED, OffsetDateTime.now());
    }

    @Transactional
    public void markFailed(Long id) {
        repository.markStatus(id, PmcS3TrackerStatus.FAILED, OffsetDateTime.now());
    }

    /**
     * Atomically increments the processed count at the DB level.
     * Safe under concurrent virtual-thread execution.
     */
    @Transactional
    public void incrementProcessed(Long id) {
        repository.incrementProcessed(id);
    }

    /**
     * Atomically increments the per-reason skip counter and the aggregate
     * {@code total_skipped} counter in a single DB statement.
     */
    @Transactional
    public void incrementSkipped(Long id, SkipReason reason) {
        switch (reason) {
            case LICENSE          -> repository.incrementSkippedLicense(id);
            case MISSING_METADATA -> repository.incrementSkippedMissingMetadata(id);
            case MISSING_JATS     -> repository.incrementSkippedMissingJats(id);
            case DUPLICATE        -> repository.incrementSkippedDuplicate(id);
            case IO               -> repository.incrementSkippedIo(id);
            case INTERRUPTED      -> repository.incrementSkippedInterrupted(id);
        }
    }

    /**
     * Re-fetches the tracker by id, returning the latest persisted state.
     * Used to obtain a fresh snapshot of the per-reason counters before
     * emitting aggregate log lines.
     */
    public Optional<PmcS3Tracker> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<PmcS3Tracker> findLatestRunning() {
        return repository.findTopByStatusOrderByStartedAtDesc(PmcS3TrackerStatus.RUNNING);
    }
}
