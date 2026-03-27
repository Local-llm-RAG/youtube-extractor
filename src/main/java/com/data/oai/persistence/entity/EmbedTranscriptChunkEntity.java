package com.data.oai.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(
        name = "embed_transcript_chunk",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_embed_transcript_chunk_section_chunk",
                columnNames = {"section_id", "embedding_model", "task", "chunk_index"}
        ),
        indexes = {
                @Index(name = "ix_embed_transcript_chunk_section", columnList = "section_id"),
                @Index(name = "ix_embed_transcript_chunk_section_chunk", columnList = "section_id, chunk_index"),
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
    @JoinColumn(
            name = "section_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_embed_transcript_chunk_section")
    )
    private SectionEntity section;

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
