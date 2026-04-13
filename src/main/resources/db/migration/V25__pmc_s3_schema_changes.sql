-- V25: Schema changes for PMC S3 direct integration
--
-- 1. Rename oai_identifier -> external_identifier (column + constraint)
-- 2. Rename tei_xml -> source_xml
-- 3. Add orcid column to record_author
-- 4. Add funding_list column to record_document
-- 5. Create pmc_s3_tracker table with sequence
--
-- This migration is written to be fully idempotent so it can be re-run
-- safely against databases where it has already been applied in whole or
-- in part (see the project-wide migration rule in CLAUDE.md).

-- ============================================================
-- 1. Rename oai_identifier -> external_identifier
-- ============================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'source_record'
          AND column_name = 'oai_identifier'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'source_record'
          AND column_name = 'external_identifier'
    ) THEN
        ALTER TABLE source_record RENAME COLUMN oai_identifier TO external_identifier;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_publication_record_oai_identifier'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_source_record_external_identifier'
    ) THEN
        ALTER TABLE source_record RENAME CONSTRAINT uq_publication_record_oai_identifier
            TO uq_source_record_external_identifier;
    END IF;
END $$;

-- ============================================================
-- 2. Rename tei_xml -> source_xml
-- ============================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'record_document'
          AND column_name = 'tei_xml'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'record_document'
          AND column_name = 'source_xml'
    ) THEN
        ALTER TABLE record_document RENAME COLUMN tei_xml TO source_xml;
    END IF;
END $$;

-- ============================================================
-- 3. Add orcid to record_author
-- ============================================================
ALTER TABLE record_author ADD COLUMN IF NOT EXISTS orcid VARCHAR(64);

-- ============================================================
-- 4. Add funding_list to record_document
-- ============================================================
ALTER TABLE record_document ADD COLUMN IF NOT EXISTS funding_list TEXT[];

-- ============================================================
-- 5. Create pmc_s3_tracker table
-- ============================================================
CREATE SEQUENCE IF NOT EXISTS pmc_s3_tracker_id_seq INCREMENT BY 50 START WITH 1;

CREATE TABLE IF NOT EXISTS pmc_s3_tracker (
    id                 BIGINT       PRIMARY KEY DEFAULT nextval('pmc_s3_tracker_id_seq'),
    batch_id           VARCHAR(255) NOT NULL,
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    total_discovered   INTEGER      NOT NULL DEFAULT 0,
    total_processed    INTEGER      NOT NULL DEFAULT 0,
    total_skipped      INTEGER      NOT NULL DEFAULT 0,
    status             VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',

    CONSTRAINT uq_pmc_s3_tracker_batch_id UNIQUE (batch_id)
);

CREATE INDEX IF NOT EXISTS idx_pmc_s3_tracker_status ON pmc_s3_tracker (status);
