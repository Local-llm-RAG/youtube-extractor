ALTER TABLE public.embed_transcript_chunk
ALTER COLUMN id TYPE BIGINT;

CREATE SEQUENCE IF NOT EXISTS public.embed_transcript_chunk_id_seq;

ALTER TABLE public.embed_transcript_chunk
    ALTER COLUMN id SET DEFAULT nextval('public.embed_transcript_chunk_id_seq');

ALTER SEQUENCE public.embed_transcript_chunk_id_seq
    OWNED BY public.embed_transcript_chunk.id;