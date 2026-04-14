package com.data.pmcs3.persistence.entity;

/**
 * Lifecycle states for a {@link PmcS3Tracker} batch run.
 *
 * <p>Stored as a {@code VARCHAR(32)} column via {@code @Enumerated(EnumType.STRING)},
 * so the persisted values are the enum constant names: {@code RUNNING},
 * {@code COMPLETED}, {@code FAILED}.  These are identical to the string
 * constants that were previously held in {@code PmcS3TrackerService}, so
 * no data migration is required for the column values themselves.
 */
public enum PmcS3TrackerStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
