package com.data.pmcs3.persistence;

import com.data.pmcs3.persistence.entity.PmcS3Tracker;
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

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

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
                .status(STATUS_RUNNING)
                .build();
        return repository.save(tracker);
    }

    @Transactional
    public void updateDiscovered(Long id, int totalDiscovered) {
        repository.findById(id).ifPresent(t -> {
            t.setTotalDiscovered(totalDiscovered);
            repository.save(t);
        });
    }

    @Transactional
    public void markCompleted(Long id) {
        repository.findById(id).ifPresent(t -> {
            t.setStatus(STATUS_COMPLETED);
            repository.save(t);
        });
    }

    @Transactional
    public void markFailed(Long id) {
        repository.findById(id).ifPresent(t -> {
            t.setStatus(STATUS_FAILED);
            repository.save(t);
        });
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
     * Atomically increments the license-rejection skip counter along with
     * {@code total_skipped}. Used when a record's license is not on the
     * commercially-usable allow-list.
     */
    @Transactional
    public void incrementSkippedLicense(Long id) {
        repository.incrementSkippedLicense(id);
    }

    /**
     * Atomically increments the missing-metadata skip counter along with
     * {@code total_skipped}. Used when no JSON metadata exists for a PMC id.
     */
    @Transactional
    public void incrementSkippedMissingMetadata(Long id) {
        repository.incrementSkippedMissingMetadata(id);
    }

    /**
     * Atomically increments the missing-JATS skip counter along with
     * {@code total_skipped}. Used when the JATS XML is missing, empty, or
     * the metadata record advertises no {@code xml_url} (author manuscripts).
     */
    @Transactional
    public void incrementSkippedMissingJats(Long id) {
        repository.incrementSkippedMissingJats(id);
    }

    /**
     * Atomically increments the duplicate skip counter along with
     * {@code total_skipped}. Used when persistence raises
     * {@link org.springframework.dao.DataIntegrityViolationException} — most
     * commonly a unique-constraint violation on {@code source_identifier}.
     */
    @Transactional
    public void incrementSkippedDuplicate(Long id) {
        repository.incrementSkippedDuplicate(id);
    }

    /**
     * Atomically increments the I/O skip counter along with
     * {@code total_skipped}. Used as the catch-all for any other exception
     * raised during per-article processing (HTTP failures, parsing errors,
     * etc.).
     */
    @Transactional
    public void incrementSkippedIo(Long id) {
        repository.incrementSkippedIo(id);
    }

    /**
     * Atomically increments the interrupted skip counter along with
     * {@code total_skipped}. Used when the per-article worker is interrupted
     * while acquiring the in-flight semaphore during shutdown.
     */
    @Transactional
    public void incrementSkippedInterrupted(Long id) {
        repository.incrementSkippedInterrupted(id);
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
        return repository.findTopByStatusOrderByStartedAtDesc(STATUS_RUNNING);
    }
}
