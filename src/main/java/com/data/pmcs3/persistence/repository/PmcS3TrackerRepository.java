package com.data.pmcs3.persistence.repository;

import com.data.pmcs3.persistence.entity.PmcS3Tracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PmcS3TrackerRepository extends JpaRepository<PmcS3Tracker, Long> {

    Optional<PmcS3Tracker> findByBatchId(String batchId);

    Optional<PmcS3Tracker> findTopByStatusOrderByStartedAtDesc(String status);

    @Modifying
    @Query("update PmcS3Tracker t set t.totalProcessed = t.totalProcessed + 1 where t.id = :id")
    int incrementProcessed(@Param("id") Long id);

    // ------------------------------------------------------------------
    // Per-reason skip counters.
    //
    // Each method atomically bumps the reason-specific counter AND the
    // aggregate total_skipped counter in a single UPDATE, so total_skipped
    // always equals the sum of the per-reason columns without any
    // application-side coordination.
    // ------------------------------------------------------------------

    @Modifying
    @Query(value = "UPDATE pmc_s3_tracker "
            + "SET skipped_license = skipped_license + 1, total_skipped = total_skipped + 1 "
            + "WHERE id = :id", nativeQuery = true)
    int incrementSkippedLicense(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE pmc_s3_tracker "
            + "SET skipped_missing_metadata = skipped_missing_metadata + 1, total_skipped = total_skipped + 1 "
            + "WHERE id = :id", nativeQuery = true)
    int incrementSkippedMissingMetadata(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE pmc_s3_tracker "
            + "SET skipped_missing_jats = skipped_missing_jats + 1, total_skipped = total_skipped + 1 "
            + "WHERE id = :id", nativeQuery = true)
    int incrementSkippedMissingJats(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE pmc_s3_tracker "
            + "SET skipped_duplicate = skipped_duplicate + 1, total_skipped = total_skipped + 1 "
            + "WHERE id = :id", nativeQuery = true)
    int incrementSkippedDuplicate(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE pmc_s3_tracker "
            + "SET skipped_io = skipped_io + 1, total_skipped = total_skipped + 1 "
            + "WHERE id = :id", nativeQuery = true)
    int incrementSkippedIo(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE pmc_s3_tracker "
            + "SET skipped_interrupted = skipped_interrupted + 1, total_skipped = total_skipped + 1 "
            + "WHERE id = :id", nativeQuery = true)
    int incrementSkippedInterrupted(@Param("id") Long id);
}
