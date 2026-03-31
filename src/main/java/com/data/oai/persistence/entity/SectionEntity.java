package com.data.oai.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
        name = "document_section",
        indexes = @Index(name = "idx_document_section_document_id", columnList = "document_id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_document_section_document_pos", columnNames = {"document_id", "pos"})
)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "document_section_seq")
    @SequenceGenerator(name = "document_section_seq", sequenceName = "document_section_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "document_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_document_section_document")
    )
    private PaperDocumentEntity document;

    @Column(name = "title", nullable = false)
    private String title = "UNTITLED";

    @Column(name = "level")
    private Integer level;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text = "";

    @Column(name = "pos", nullable = false)
    private Integer pos;

    @OneToMany(
            mappedBy = "section",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("chunkIndex ASC")
    private List<EmbedTranscriptChunkEntity> embeddings = new ArrayList<>();

    public void addEmbedding(EmbedTranscriptChunkEntity emb) {
        embeddings.add(emb);
        emb.setSection(this);
    }

    public void removeEmbedding(EmbedTranscriptChunkEntity emb) {
        embeddings.remove(emb);
        emb.setSection(null);
    }
}
