package com.youtube.jpa.dao.arxiv;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(
    name = "arxiv_section",
    indexes = @Index(name = "idx_arxiv_section_document_id", columnList = "document_id"),
    uniqueConstraints = @UniqueConstraint(name = "uq_section_document_pos", columnNames = {"document_id", "pos"})
)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArxivSectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
        name = "document_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_section_document")
    )
    private ArxivPaperDocumentEntity document;

    @Column(name = "title", nullable = false, length = 512)
    private String title = "UNTITLED";

    @Column(name = "level")
    private Integer level;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text = "";

    @Column(name = "pos", nullable = false)
    private Integer pos;
}
