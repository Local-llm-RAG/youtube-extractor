package com.data.pmcs3.persistence;

/**
 * Classifies the reason a PMC S3 article was skipped during batch processing.
 * Used by {@link PmcS3TrackerService#incrementSkipped(Long, SkipReason)} to
 * dispatch to the correct per-column atomic counter in the repository.
 */
public enum SkipReason {
    LICENSE,
    MISSING_METADATA,
    MISSING_JATS,
    DUPLICATE,
    IO,
    INTERRUPTED
}
