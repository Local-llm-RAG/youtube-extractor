-- V1__create_arxiv_tables.sql
-- Schema for ArxivRecord + Document + Authors + Categories + Sections

CREATE TABLE arxiv_record
(
    id             BIGSERIAL PRIMARY KEY,
    arxiv_id       VARCHAR(64) NOT NULL,
    oai_identifier VARCHAR(255),
    datestamp      Date,
    title          TEXT,
    abstract_text  TEXT,
    comments       TEXT,
    journal_ref    TEXT,
    doi            VARCHAR(255),
    license        VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_arxiv_record_arxiv_id UNIQUE (arxiv_id),
    CONSTRAINT uq_arxiv_record_oai_identifier UNIQUE (oai_identifier)
);

CREATE TABLE arxiv_record_category
(
    record_id BIGINT       NOT NULL,
    category  VARCHAR(128) NOT NULL,
    PRIMARY KEY (record_id),
    CONSTRAINT fk_category_record
        FOREIGN KEY (record_id)
            REFERENCES arxiv_record (id)
            ON DELETE CASCADE,
    CONSTRAINT ck_category_not_blank CHECK (BTRIM(category) <> '')
);

CREATE INDEX idx_arxiv_record_category_record_id ON arxiv_record_category (record_id);

CREATE TABLE arxiv_author
(
    id         BIGSERIAL PRIMARY KEY,
    record_id  BIGINT  NOT NULL,
    pos        INTEGER NOT NULL,
    first_name VARCHAR(128),
    last_name  VARCHAR(128),
    CONSTRAINT fk_author_record
        FOREIGN KEY (record_id)
            REFERENCES arxiv_record (id)
            ON DELETE CASCADE,
    CONSTRAINT uq_author_record_pos UNIQUE (record_id, pos)
);

CREATE INDEX idx_arxiv_author_record_id ON arxiv_author (record_id);
CREATE TABLE arxiv_paper_document
(
    id             BIGSERIAL PRIMARY KEY,
    record_id      BIGINT      NOT NULL,
    title          TEXT,
    abstract_text  TEXT,
    tei_xml_raw    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_document_record
        FOREIGN KEY (record_id)
            REFERENCES arxiv_record (id)
            ON DELETE CASCADE,
    CONSTRAINT uq_document_record UNIQUE (record_id)
);

CREATE INDEX idx_arxiv_paper_document_record_id ON arxiv_paper_document (record_id);

CREATE TABLE arxiv_section
(
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT       NOT NULL,
    pos         INTEGER      NOT NULL,
    title       VARCHAR(512) NOT NULL DEFAULT 'UNTITLED',
    level       INTEGER,
    text        TEXT         NOT NULL DEFAULT '',
    CONSTRAINT fk_section_document
        FOREIGN KEY (document_id)
            REFERENCES arxiv_paper_document (id)
            ON DELETE CASCADE,
    CONSTRAINT uq_section_document_pos UNIQUE (document_id, pos),
    CONSTRAINT ck_section_title_not_blank CHECK (BTRIM(title) <> '')
);

CREATE INDEX idx_arxiv_section_document_id ON arxiv_section (document_id);
