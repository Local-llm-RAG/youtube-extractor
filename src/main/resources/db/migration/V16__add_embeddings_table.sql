CREATE TABLE IF NOT EXISTS embed_transcript_chunk
(
    id BIGINT PRIMARY KEY,
    record_document_id BIGINT NOT NULL,
    task TEXT NULL,
    chunk_tokens INTEGER NULL,
    chunk_overlap INTEGER NULL,
    embedding_model TEXT NOT NULL,
    dim INTEGER NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    span_start INTEGER NULL,
    span_end INTEGER NULL,
    embedding REAL [] NOT NULL,
    CONSTRAINT fk_embed_transcript_chunk_paper
    FOREIGN KEY(record_document_id) REFERENCES record_document(id) ON DELETE CASCADE,
    CONSTRAINT uq_embed_transcript_chunk_doc_chunk
    UNIQUE(record_document_id, embedding_model, task, chunk_index),
    CHECK (array_length(embedding, 1) = dim)
);

CREATE INDEX IF NOT EXISTS ix_embed_transcript_chunk_paper
    ON embed_transcript_chunk(record_document_id);

CREATE INDEX IF NOT EXISTS ix_embed_transcript_chunk_paper_chunk
    ON embed_transcript_chunk(record_document_id, chunk_index);

CREATE INDEX IF NOT EXISTS ix_embed_transcript_chunk_model_task
    ON embed_transcript_chunk(embedding_model, task);