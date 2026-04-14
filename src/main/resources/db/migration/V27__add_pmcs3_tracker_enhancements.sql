-- V27: PMC S3 tracker enhancements
--
-- 1. Add completed_at column (nullable TIMESTAMPTZ) to record when a batch
--    finished, whether successfully or with a failure.
-- 2. Backfill any NULL counter values to 0 (defensive — all columns were
--    created NOT NULL DEFAULT 0 in V25/V26, so this is a no-op in practice).
-- 3. Tighten the counter columns to NOT NULL (idempotent: columns that are
--    already NOT NULL accept this ALTER as a no-op in PostgreSQL).
--
-- status column: already VARCHAR(32) in V25, which comfortably holds the
-- longest enum constant name "COMPLETED" (9 chars). No widening needed.
--
-- This migration is idempotent and safe to re-run.

-- 1. Add completed_at column
ALTER TABLE pmc_s3_tracker
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

-- 2. Backfill NULL counters to 0
UPDATE pmc_s3_tracker SET total_discovered           = 0 WHERE total_discovered           IS NULL;
UPDATE pmc_s3_tracker SET total_processed            = 0 WHERE total_processed            IS NULL;
UPDATE pmc_s3_tracker SET total_skipped              = 0 WHERE total_skipped              IS NULL;
UPDATE pmc_s3_tracker SET skipped_license            = 0 WHERE skipped_license            IS NULL;
UPDATE pmc_s3_tracker SET skipped_missing_metadata   = 0 WHERE skipped_missing_metadata   IS NULL;
UPDATE pmc_s3_tracker SET skipped_missing_jats       = 0 WHERE skipped_missing_jats       IS NULL;
UPDATE pmc_s3_tracker SET skipped_duplicate          = 0 WHERE skipped_duplicate          IS NULL;
UPDATE pmc_s3_tracker SET skipped_io                 = 0 WHERE skipped_io                 IS NULL;
UPDATE pmc_s3_tracker SET skipped_interrupted        = 0 WHERE skipped_interrupted        IS NULL;

-- 3. Tighten counters to NOT NULL
ALTER TABLE pmc_s3_tracker ALTER COLUMN total_discovered           SET NOT NULL;
ALTER TABLE pmc_s3_tracker ALTER COLUMN total_processed            SET NOT NULL;
ALTER TABLE pmc_s3_tracker ALTER COLUMN total_skipped              SET NOT NULL;
ALTER TABLE pmc_s3_tracker ALTER COLUMN skipped_license            SET NOT NULL;
ALTER TABLE pmc_s3_tracker ALTER COLUMN skipped_missing_metadata   SET NOT NULL;
ALTER TABLE pmc_s3_tracker ALTER COLUMN skipped_missing_jats       SET NOT NULL;
ALTER TABLE pmc_s3_tracker ALTER COLUMN skipped_duplicate          SET NOT NULL;
ALTER TABLE pmc_s3_tracker ALTER COLUMN skipped_io                 SET NOT NULL;
ALTER TABLE pmc_s3_tracker ALTER COLUMN skipped_interrupted        SET NOT NULL;
