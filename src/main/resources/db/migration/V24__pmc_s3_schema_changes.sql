-- V24: Schema changes for PMC S3 direct integration
--
-- 1. Rename oai_identifier -> external_identifier (column + constraint)
-- 2. Rename tei_xml -> source_xml
-- 3. Add orcid column to record_author
-- 4. Add funding_list column to record_document
-- 5. Create pmc_s3_tracker table with sequence

-- ============================================================
-- 1. Rename oai_identifier -> external_identifier
-- ============================================================
ALTER TABLE source_record RENAME COLUMN oai_identifier TO external_identifier;
ALTER TABLE source_record RENAME CONSTRAINT uq_publication_record_oai_identifier
    TO uq_source_record_external_identifier;

-- ============================================================
-- 2. Rename tei_xml -> source_xml
-- ============================================================
ALTER TABLE record_document RENAME COLUMN tei_xml TO source_xml;

-- ============================================================
-- 3. Add orcid to record_author
-- ============================================================
ALTER TABLE record_author ADD COLUMN orcid VARCHAR(64);

-- ============================================================
-- 4. Add funding_list to record_document
-- ============================================================
ALTER TABLE record_document ADD COLUMN funding_list TEXT[];

-- ============================================================
-- 5. Create pmc_s3_tracker table
-- ============================================================
CREATE SEQUENCE pmc_s3_tracker_id_seq INCREMENT BY 50 START WITH 1;

CREATE TABLE pmc_s3_tracker (
    id                 BIGINT       PRIMARY KEY DEFAULT nextval('pmc_s3_tracker_id_seq'),
    batch_id           VARCHAR(255) NOT NULL,
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    total_discovered   INTEGER      NOT NULL DEFAULT 0,
    total_processed    INTEGER      NOT NULL DEFAULT 0,
    total_skipped      INTEGER      NOT NULL DEFAULT 0,
    status             VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',

    CONSTRAINT uq_pmc_s3_tracker_batch_id UNIQUE (batch_id)
);

CREATE INDEX idx_pmc_s3_tracker_status ON pmc_s3_tracker (status);
