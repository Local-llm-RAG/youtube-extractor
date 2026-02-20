package com.youtube.arxiv.oai.record;

import com.youtube.arxiv.oai.DataSource;
import com.youtube.arxiv.oai.author.ArxivAuthorEntity;
import com.youtube.arxiv.oai.paper.ArxivPaperDocumentEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
    name = "arxiv_record",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_arxiv_record_arxiv_id", columnNames = "arxiv_id"),
        @UniqueConstraint(name = "uq_arxiv_record_oai_identifier", columnNames = "oai_identifier")
    }
)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArxivRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arxiv_id", nullable = false, length = 64)
    private String arxivId;

    @Column(name = "oai_identifier", length = 255)
    private String oaiIdentifier;

    @Column(name = "datestamp")
    private LocalDate datestamp;

    @Column(name = "comments", columnDefinition = "text")
    private String comments;

    @Column(name = "journal_ref", columnDefinition = "text")
    private String journalRef;

    @Column(name = "doi", length = 255)
    private String doi;

    @Column(name = "license", length = 255)
    private String license;

    @ElementCollection
    @CollectionTable(
        name = "arxiv_record_category",
        joinColumns = @JoinColumn(name = "record_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_category_record"))
    )
    @Column(name = "category", nullable = false, length = 128)
    private List<String> categories = new ArrayList<>();

    @OneToMany(
        mappedBy = "record",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderColumn(name = "pos")
    private List<ArxivAuthorEntity> authors = new ArrayList<>();

    @OneToOne(
        mappedBy = "record",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private ArxivPaperDocumentEntity document;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "data_source", nullable = false)
    @Enumerated(EnumType.STRING)
    private DataSource dataSource;
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

    public void addAuthor(ArxivAuthorEntity author) {
        authors.add(author);
        author.setRecord(this);
    }

    public void removeAuthor(ArxivAuthorEntity author) {
        authors.remove(author);
        author.setRecord(null);
    }

    public void setDocument(ArxivPaperDocumentEntity doc) {
        this.document = doc;
        if (doc != null) doc.setRecord(this);
    }
}
