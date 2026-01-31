UPDATE public.video_transcripts
SET language = 'bg'
WHERE language IS NULL OR btrim(language) = '';

ALTER TABLE public.video_transcripts
    ALTER COLUMN language SET DEFAULT 'bg';

ALTER TABLE public.videos
DROP COLUMN IF EXISTS category_id,
  DROP COLUMN IF EXISTS category_title;
