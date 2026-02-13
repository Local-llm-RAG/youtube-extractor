ALTER TABLE arxiv_record_category
DROP CONSTRAINT arxiv_record_category_pkey;

ALTER TABLE arxiv_record_category
    ADD CONSTRAINT arxiv_record_category_pkey PRIMARY KEY (record_id, category);