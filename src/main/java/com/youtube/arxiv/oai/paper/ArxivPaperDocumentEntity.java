package com.youtube.arxiv.oai.paper;

import com.youtube.arxiv.oai.record.ArxivRecordEntity;
import com.youtube.arxiv.oai.section.ArxivSectionEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
    name = "arxiv_paper_document",
    indexes = {
        @Index(name = "idx_arxiv_paper_document_record_id", columnList = "record_id"),
    },
    uniqueConstraints = @UniqueConstraint(name = "uq_document_record", columnNames = "record_id")
)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArxivPaperDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
        name = "record_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_document_record")
    )
    private ArxivRecordEntity record;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "abstract_text", columnDefinition = "text")
    private String abstractText;

    @Column(name = "tei_xml_raw", columnDefinition = "text")
    private String teiXmlRaw;

    @OneToMany(
        mappedBy = "document",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderColumn(name = "pos")
    private List<ArxivSectionEntity> sections = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        var now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void addSection(ArxivSectionEntity section) {
        sections.add(section);
        section.setDocument(this);
    }

    public void removeSection(ArxivSectionEntity section) {
        sections.remove(section);
        section.setDocument(null);
    }
}
