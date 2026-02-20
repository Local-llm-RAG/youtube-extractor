-- 1) Tables
ALTER TABLE public.arxiv_record RENAME TO source_record;
ALTER TABLE public.arxiv_tracker RENAME TO tracker;
ALTER TABLE public.arxiv_author RENAME TO record_author;
ALTER TABLE public.arxiv_paper_document RENAME TO record_document;
ALTER TABLE public.arxiv_record_category RENAME TO record_category;
ALTER TABLE public.arxiv_section RENAME TO document_section;

-- 2) Columns (arXiv-specific -> generic)
ALTER TABLE public.source_record RENAME COLUMN arxiv_id TO source_identifier;

ALTER TABLE public.record_document RENAME COLUMN abstract_text TO abstract;
ALTER TABLE public.record_document RENAME COLUMN tei_xml_raw TO tei_xml;

ALTER INDEX public.idx_arxiv_author_record_id RENAME TO idx_record_author_record_id;
ALTER INDEX public.idx_arxiv_paper_document_record_id RENAME TO idx_record_document_record_id;
ALTER INDEX public.idx_arxiv_record_category_record_id RENAME TO idx_record_category_record_id;
ALTER INDEX public.idx_arxiv_section_document_id RENAME TO idx_document_section_document_id;

      -- source_record constraints
ALTER TABLE public.source_record RENAME CONSTRAINT arxiv_record_pkey TO publication_record_pkey;
ALTER TABLE public.source_record RENAME CONSTRAINT uq_arxiv_record_arxiv_id TO uq_publication_record_source_identifier;
ALTER TABLE public.source_record RENAME CONSTRAINT uq_arxiv_record_oai_identifier TO uq_publication_record_oai_identifier;

-- tracker
ALTER TABLE public.tracker RENAME CONSTRAINT arxiv_tracker_pkey TO ingestion_tracker_pkey;

-- record_author
ALTER TABLE public.record_author RENAME CONSTRAINT arxiv_author_pkey TO record_author_pkey;
ALTER TABLE public.record_author RENAME CONSTRAINT uq_author_record_pos TO uq_record_author_record_pos;
ALTER TABLE public.record_author RENAME CONSTRAINT fk_author_record TO fk_record_author_record;

-- record_document
ALTER TABLE public.record_document RENAME CONSTRAINT arxiv_paper_document_pkey TO record_document_pkey;
ALTER TABLE public.record_document RENAME CONSTRAINT uq_document_record TO uq_record_document_record;
ALTER TABLE public.record_document RENAME CONSTRAINT fk_document_record TO fk_record_document_record;

-- record_category
ALTER TABLE public.record_category RENAME CONSTRAINT arxiv_record_category_pkey TO record_category_pkey;
ALTER TABLE public.record_category RENAME CONSTRAINT ck_category_not_blank TO ck_record_category_not_blank;
ALTER TABLE public.record_category RENAME CONSTRAINT fk_category_record TO fk_record_category_record;

-- document_section
ALTER TABLE public.document_section RENAME CONSTRAINT arxiv_section_pkey TO document_section_pkey;
ALTER TABLE public.document_section RENAME CONSTRAINT ck_section_title_not_blank TO ck_document_section_title_not_blank;
ALTER TABLE public.document_section RENAME CONSTRAINT uq_section_document_pos TO uq_document_section_document_pos;
ALTER TABLE public.document_section RENAME CONSTRAINT fk_section_document TO fk_document_section_document;