DO $$
DECLARE
c_name text;
BEGIN
SELECT con.conname INTO c_name
FROM pg_constraint con
         JOIN pg_class rel ON rel.oid = con.conrelid
         JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
WHERE nsp.nspname = 'public'
  AND rel.relname = 'video_transcripts'
  AND con.contype = 'u'
  AND pg_get_constraintdef(con.oid) ILIKE '%(video_id)%'
  LIMIT 1;

IF c_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE public.video_transcripts DROP CONSTRAINT %I', c_name);
END IF;
END $$;

DO $$
DECLARE
i_name text;
BEGIN
SELECT idx.relname INTO i_name
FROM pg_index i
         JOIN pg_class idx ON idx.oid = i.indexrelid
         JOIN pg_class tbl ON tbl.oid = i.indrelid
         JOIN pg_namespace nsp ON nsp.oid = tbl.relnamespace
WHERE nsp.nspname = 'public'
  AND tbl.relname = 'video_transcripts'
  AND i.indisunique = true
  AND pg_get_indexdef(i.indexrelid) ILIKE '%(video_id)%'
  LIMIT 1;

IF i_name IS NOT NULL THEN
    EXECUTE format('DROP INDEX IF EXISTS public.%I', i_name);
END IF;
END $$;
ALTER TABLE public.video_transcripts
    ADD COLUMN IF NOT EXISTS category_id text[],
    ADD COLUMN IF NOT EXISTS category_title text[],
    ADD COLUMN IF NOT EXISTS language text;

UPDATE public.video_transcripts vt
SET
    category_id = v.category_id,
    category_title = v.category_title
    FROM public.videos v
WHERE v.id = vt.video_id;

CREATE INDEX IF NOT EXISTS idx_video_transcripts_video_id
    ON public.video_transcripts(video_id);
