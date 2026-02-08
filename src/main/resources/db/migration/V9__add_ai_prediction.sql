alter table video_transcripts
    add column if not exists ai_transformed_transcript_text varchar,
    add column if not exists ai_token_count numeric,
    add column if not exists ai_transformation_cost decimal