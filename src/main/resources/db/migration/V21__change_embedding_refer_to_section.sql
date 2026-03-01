-- drop the old table (since no data)
drop table if exists embed_transcript_chunk;

create table if not exists embed_transcript_chunk (
                                        id bigserial primary key,
                                        section_id bigint not null,
                                        task text,
                                        chunk_tokens integer,
                                        chunk_overlap integer,
                                        embedding_model text not null,
                                        dim integer not null,
                                        chunk_index integer not null,
                                        chunk_text text not null,
                                        span_start integer,
                                        span_end integer,
                                        embedding real[] not null,
                                        constraint fk_embed_transcript_chunk_section
                                            foreign key (section_id) references document_section(id)
);

create unique index uq_embed_transcript_chunk_section_chunk
    on embed_transcript_chunk(section_id, embedding_model, task, chunk_index);

create index ix_embed_transcript_chunk_section
    on embed_transcript_chunk(section_id);

create index ix_embed_transcript_chunk_section_chunk
    on embed_transcript_chunk(section_id, chunk_index);

create index ix_embed_transcript_chunk_model_task
    on embed_transcript_chunk(embedding_model, task);