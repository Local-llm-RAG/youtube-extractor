ALTER TABLE record_document
DROP COLUMN IF EXISTS reference_list;

CREATE TABLE IF NOT EXISTS reference_mention
(
    id                 BIGSERIAL PRIMARY KEY,
    record_document_id BIGINT  NOT NULL,
    ref_index          INTEGER NOT NULL,
    title              TEXT,
    doi                TEXT,
    year               VARCHAR(10),
    venue              TEXT,
    authors            TEXT[],
    urls               TEXT[],
    idnos              TEXT[],

    CONSTRAINT fk_reference_record FOREIGN KEY (record_document_id) REFERENCES record_document (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reference_record_id
    ON reference_mention (record_document_id);

CREATE INDEX IF NOT EXISTS idx_reference_doi
    ON reference_mention (doi);