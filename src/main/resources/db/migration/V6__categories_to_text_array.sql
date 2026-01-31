ALTER TABLE videos
ALTER COLUMN category_id TYPE text[]
  USING CASE
    WHEN category_id IS NULL THEN '{}'::text[]
    ELSE ARRAY[category_id::text]
END;

ALTER TABLE videos
ALTER COLUMN category_title TYPE text[]
  USING CASE
    WHEN category_title IS NULL THEN '{}'::text[]
    ELSE ARRAY[category_title::text]
END;
