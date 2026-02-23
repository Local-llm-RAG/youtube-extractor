package com.data.oai.generic.common.section;

import com.data.oai.generic.common.paper.PaperDocumentEntity;
import jakarta.persistence.*;
import lombok.*;

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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
}
