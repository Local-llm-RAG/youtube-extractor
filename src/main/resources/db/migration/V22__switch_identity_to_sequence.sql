-- V22: Switch ID generation from IDENTITY (bigserial) to explicit SEQUENCE
-- for Hibernate batch insert optimization (allocationSize = 50).
--
-- Steps per table:
--   1. Remove bigserial ownership so sequence isn't auto-dropped
--   2. Rename sequence to match current table name (where needed)
--   3. Reset sequence value to MAX(id) + 1
--   4. Set INCREMENT BY 50 to match JPA allocationSize
--   5. Set column default explicitly

-- ============================================================
-- 1. source_record  (was arxiv_record -> seq: arxiv_record_id_seq)
-- ============================================================
ALTER SEQUENCE arxiv_record_id_seq OWNED BY NONE;
ALTER SEQUENCE arxiv_record_id_seq RENAME TO source_record_id_seq;
SELECT setval('source_record_id_seq', COALESCE((SELECT MAX(id) FROM source_record), 0) + 1, false);
ALTER SEQUENCE source_record_id_seq INCREMENT BY 50;
ALTER TABLE source_record ALTER COLUMN id SET DEFAULT nextval('source_record_id_seq');

-- ============================================================
-- 2. record_document  (was arxiv_paper_document -> seq: arxiv_paper_document_id_seq)
-- ============================================================
ALTER SEQUENCE arxiv_paper_document_id_seq OWNED BY NONE;
ALTER SEQUENCE arxiv_paper_document_id_seq RENAME TO record_document_id_seq;
SELECT setval('record_document_id_seq', COALESCE((SELECT MAX(id) FROM record_document), 0) + 1, false);
ALTER SEQUENCE record_document_id_seq INCREMENT BY 50;
ALTER TABLE record_document ALTER COLUMN id SET DEFAULT nextval('record_document_id_seq');

-- ============================================================
-- 3. document_section  (was arxiv_section -> seq: arxiv_section_id_seq)
-- ============================================================
ALTER SEQUENCE arxiv_section_id_seq OWNED BY NONE;
ALTER SEQUENCE arxiv_section_id_seq RENAME TO document_section_id_seq;
SELECT setval('document_section_id_seq', COALESCE((SELECT MAX(id) FROM document_section), 0) + 1, false);
ALTER SEQUENCE document_section_id_seq INCREMENT BY 50;
ALTER TABLE document_section ALTER COLUMN id SET DEFAULT nextval('document_section_id_seq');

-- ============================================================
-- 4. record_author  (was arxiv_author -> seq: arxiv_author_id_seq)
-- ============================================================
ALTER SEQUENCE arxiv_author_id_seq OWNED BY NONE;
ALTER SEQUENCE arxiv_author_id_seq RENAME TO record_author_id_seq;
SELECT setval('record_author_id_seq', COALESCE((SELECT MAX(id) FROM record_author), 0) + 1, false);
ALTER SEQUENCE record_author_id_seq INCREMENT BY 50;
ALTER TABLE record_author ALTER COLUMN id SET DEFAULT nextval('record_author_id_seq');

-- ============================================================
-- 5. tracker  (was arxiv_tracker -> seq: arxiv_tracker_id_seq)
-- ============================================================
ALTER SEQUENCE arxiv_tracker_id_seq OWNED BY NONE;
ALTER SEQUENCE arxiv_tracker_id_seq RENAME TO tracker_id_seq;
SELECT setval('tracker_id_seq', COALESCE((SELECT MAX(id) FROM tracker), 0) + 1, false);
ALTER SEQUENCE tracker_id_seq INCREMENT BY 50;
ALTER TABLE tracker ALTER COLUMN id SET DEFAULT nextval('tracker_id_seq');

-- ============================================================
-- 6. reference_mention  (seq already named: reference_mention_id_seq)
-- ============================================================
ALTER SEQUENCE reference_mention_id_seq OWNED BY NONE;
SELECT setval('reference_mention_id_seq', COALESCE((SELECT MAX(id) FROM reference_mention), 0) + 1, false);
ALTER SEQUENCE reference_mention_id_seq INCREMENT BY 50;
ALTER TABLE reference_mention ALTER COLUMN id SET DEFAULT nextval('reference_mention_id_seq');

-- ============================================================
-- 7. embed_transcript_chunk  (seq already named: embed_transcript_chunk_id_seq)
-- ============================================================
ALTER SEQUENCE embed_transcript_chunk_id_seq OWNED BY NONE;
SELECT setval('embed_transcript_chunk_id_seq', COALESCE((SELECT MAX(id) FROM embed_transcript_chunk), 0) + 1, false);
ALTER SEQUENCE embed_transcript_chunk_id_seq INCREMENT BY 50;
ALTER TABLE embed_transcript_chunk ALTER COLUMN id SET DEFAULT nextval('embed_transcript_chunk_id_seq');

-- ============================================================
-- 8. channels  (seq already named: channels_id_seq)
-- ============================================================
ALTER SEQUENCE channels_id_seq OWNED BY NONE;
SELECT setval('channels_id_seq', COALESCE((SELECT MAX(id) FROM channels), 0) + 1, false);
ALTER SEQUENCE channels_id_seq INCREMENT BY 50;
ALTER TABLE channels ALTER COLUMN id SET DEFAULT nextval('channels_id_seq');

-- ============================================================
-- 9. videos  (seq already named: videos_id_seq)
-- ============================================================
ALTER SEQUENCE videos_id_seq OWNED BY NONE;
SELECT setval('videos_id_seq', COALESCE((SELECT MAX(id) FROM videos), 0) + 1, false);
ALTER SEQUENCE videos_id_seq INCREMENT BY 50;
ALTER TABLE videos ALTER COLUMN id SET DEFAULT nextval('videos_id_seq');

-- ============================================================
-- 10. video_transcripts  (seq already named: video_transcripts_id_seq)
-- ============================================================
ALTER SEQUENCE video_transcripts_id_seq OWNED BY NONE;
SELECT setval('video_transcripts_id_seq', COALESCE((SELECT MAX(id) FROM video_transcripts), 0) + 1, false);
ALTER SEQUENCE video_transcripts_id_seq INCREMENT BY 50;
ALTER TABLE video_transcripts ALTER COLUMN id SET DEFAULT nextval('video_transcripts_id_seq');

-- ============================================================
-- 11. youtube_regions  (seq already named: youtube_regions_id_seq)
-- ============================================================
ALTER SEQUENCE youtube_regions_id_seq OWNED BY NONE;
SELECT setval('youtube_regions_id_seq', COALESCE((SELECT MAX(id) FROM youtube_regions), 0) + 1, false);
ALTER SEQUENCE youtube_regions_id_seq INCREMENT BY 50;
ALTER TABLE youtube_regions ALTER COLUMN id SET DEFAULT nextval('youtube_regions_id_seq');

-- ============================================================
-- 12. youtube_region_languages  (seq already named: youtube_region_languages_id_seq)
-- ============================================================
ALTER SEQUENCE youtube_region_languages_id_seq OWNED BY NONE;
SELECT setval('youtube_region_languages_id_seq', COALESCE((SELECT MAX(id) FROM youtube_region_languages), 0) + 1, false);
ALTER SEQUENCE youtube_region_languages_id_seq INCREMENT BY 50;
ALTER TABLE youtube_region_languages ALTER COLUMN id SET DEFAULT nextval('youtube_region_languages_id_seq');
