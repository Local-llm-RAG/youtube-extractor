-- V26: Per-reason skip counters on pmc_s3_tracker
--
-- End-to-end testing showed a ~43% skip rate on the PMC S3 pipeline, but the
-- tracker only exposed a single total_skipped counter which hid the reason
-- for each rejection. This migration adds one counter per skip reason.
--
-- total_skipped remains and becomes the sum of all per-reason counters,
-- maintained in parallel by the application for backwards compatibility
-- and quick aggregate totals.
--
-- This migration is written to be fully idempotent so it can be re-run
-- safely against databases where it has already been applied in whole or
-- in part (see the project-wide migration rule in CLAUDE.md).

ALTER TABLE pmc_s3_tracker
    ADD COLUMN IF NOT EXISTS skipped_license          INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS skipped_missing_metadata INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS skipped_missing_jats     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS skipped_duplicate        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS skipped_io               INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS skipped_interrupted      INTEGER NOT NULL DEFAULT 0;
