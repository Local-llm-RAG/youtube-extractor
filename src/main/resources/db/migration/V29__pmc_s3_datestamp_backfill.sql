-- V29: Backfill NULL datestamps for existing PMC_S3 rows using created_at.
--
-- Prior to the datestamp extraction pipeline change that accompanies this
-- migration, PMC_S3 rows could land with a NULL `source_record.datestamp`
-- whenever the JSON metadata's `publication_date` field was empty. Going
-- forward the facade resolves datestamp from JATS <pub-date> elements first,
-- so new ingests effectively always carry a real publication date. This
-- migration patches the historical rows so every PMC_S3 record has a usable
-- date for window queries.
--
-- Strategy: stamp any NULL PMC_S3 datestamps with the row's `created_at::date`.
-- `created_at` is the ingest time, which is an acceptable proxy when the
-- publisher-supplied date is genuinely missing — it's always <= today and
-- preserves roughly chronological ordering. This is the `-- comment noting
-- the backfill source` the plan calls for.
--
-- Idempotency:
-- - The `WHERE datestamp IS NULL AND data_source = 'PMC_S3'` predicate makes
--   the UPDATE a no-op on re-run — after the first pass there are no NULL
--   rows to touch.
-- - The migration makes no schema change, only data backfill.
--
-- This migration is idempotent and safe to re-run.

UPDATE source_record
SET datestamp = created_at::date
WHERE datestamp IS NULL
  AND data_source = 'PMC_S3';
