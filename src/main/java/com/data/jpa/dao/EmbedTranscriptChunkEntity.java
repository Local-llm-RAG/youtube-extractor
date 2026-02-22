package com.data.jpa.dao;

import com.data.oai.generic.common.paper.PaperDocumentEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(
        name = "embed_transcript_chunk",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_embed_transcript_chunk_doc_chunk",
                columnNames = {"record_document_id", "embedding_model", "task", "chunk_index"}
        ),
        indexes = {
                @Index(name = "ix_embed_transcript_chunk_paper", columnList = "record_document_id"),
                @Index(name = "ix_embed_transcript_chunk_paper_chunk", columnList = "record_document_id, chunk_index"),
                @Index(name = "ix_embed_transcript_chunk_model_task", columnList = "embedding_model, task")
        }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmbedTranscriptChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_document_id", nullable = false)
    private PaperDocumentEntity document;

    @Column(name = "task", columnDefinition = "text")
    private String task;

    @Column(name = "chunk_tokens")
    private Integer chunkTokens;

    @Column(name = "chunk_overlap")
    private Integer chunkOverlap;

    @Column(name = "embedding_model", nullable = false, columnDefinition = "text")
    private String embeddingModel;

    @Column(name = "dim", nullable = false)
    private int dim;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "text")
    private String chunkText;

    @Column(name = "span_start")
    private Integer spanStart;

    @Column(name = "span_end")
    private Integer spanEnd;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "embedding", nullable = false, columnDefinition = "real[]")
    private List<Float> embedding;
}