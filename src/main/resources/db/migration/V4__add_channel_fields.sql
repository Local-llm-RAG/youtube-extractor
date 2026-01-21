ALTER TABLE channels
    ADD COLUMN IF NOT EXISTS description VARCHAR,
    ADD COLUMN IF NOT EXISTS country VARCHAR(255);

-- SNIPPET
ALTER TABLE videos
    ADD COLUMN IF NOT EXISTS title               TEXT,
    ADD COLUMN IF NOT EXISTS description         TEXT,
    ADD COLUMN IF NOT EXISTS published_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS category_id         VARCHAR(32),
    ADD COLUMN IF NOT EXISTS category_title      VARCHAR(128),
    ADD COLUMN IF NOT EXISTS default_language    VARCHAR(32),
    ADD COLUMN IF NOT EXISTS tags                TEXT[];

-- STATUS
ALTER TABLE videos
    ADD COLUMN IF NOT EXISTS made_for_kids       BOOLEAN,
    ADD COLUMN IF NOT EXISTS license             VARCHAR(32);

-- TOPIC DETAILS
ALTER TABLE videos
    ADD COLUMN IF NOT EXISTS topic_categories    TEXT[];

CREATE INDEX IF NOT EXISTS ix_videos_published_at   ON videos (published_at);
