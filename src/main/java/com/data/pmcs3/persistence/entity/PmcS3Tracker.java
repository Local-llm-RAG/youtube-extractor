package com.data.pmcs3.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Tracks the progress of a single PMC S3 batch run so that a restart
 * can resume from where the previous run left off.
 *
 * <p>One batch corresponds to one inventory manifest being processed.
 * The tracker is created at the start of a run and continually updated
 * as records are processed, skipped, or fail.
 */
@Entity
@Table(
    name = "pmc_s3_tracker",
    uniqueConstraints = @UniqueConstraint(name = "uq_pmc_s3_tracker_batch_id", columnNames = "batch_id"),
    indexes = @Index(name = "idx_pmc_s3_tracker_status", columnList = "status")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PmcS3Tracker {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pmc_s3_tracker_seq")
    @SequenceGenerator(name = "pmc_s3_tracker_seq", sequenceName = "pmc_s3_tracker_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "batch_id", nullable = false, length = 255)
    private String batchId;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "total_discovered", nullable = false)
    private Integer totalDiscovered;

    @Column(name = "total_processed", nullable = false)
    private Integer totalProcessed;

    @Column(name = "total_skipped", nullable = false)
    private Integer totalSkipped;

    @Builder.Default
    @Column(name = "skipped_license", nullable = false)
    private Integer skippedLicense = 0;

    @Builder.Default
    @Column(name = "skipped_missing_metadata", nullable = false)
    private Integer skippedMissingMetadata = 0;

    @Builder.Default
    @Column(name = "skipped_missing_jats", nullable = false)
    private Integer skippedMissingJats = 0;

    @Builder.Default
    @Column(name = "skipped_duplicate", nullable = false)
    private Integer skippedDuplicate = 0;

    @Builder.Default
    @Column(name = "skipped_io", nullable = false)
    private Integer skippedIo = 0;

    @Builder.Default
    @Column(name = "skipped_interrupted", nullable = false)
    private Integer skippedInterrupted = 0;

    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
