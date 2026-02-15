ALTER TABLE arxiv_paper_document
    ADD COLUMN IF NOT EXISTS raw_content text NOT NULL DEFAULT 'NO_CONTENT',
    ADD COLUMN IF NOT EXISTS keyword_List text[] NOT NULL DEFAULT ARRAY[]::text[],
    ADD COLUMN IF NOT EXISTS affiliation_list text[] NOT NULL DEFAULT ARRAY[]::text[],
    ADD COLUMN IF NOT EXISTS reference_list text[] NOT NULL DEFAULT ARRAY[]::text[],
    ADD COLUMN IF NOT EXISTS class_code_list text[] NOT NULL DEFAULT ARRAY[]::text[],
    ADD COLUMN IF NOT EXISTS doc_type varchar(128),
    DROP COLUMN IF EXISTS created_at,
    DROP COLUMN IF EXISTS updated_at;

ALTER TABLE arxiv_record
    DROP COLUMN IF EXISTS abstract_text,
    DROP COLUMN IF EXISTS title;
