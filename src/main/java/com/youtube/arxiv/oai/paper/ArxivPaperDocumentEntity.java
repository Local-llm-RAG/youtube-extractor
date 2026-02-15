package com.youtube.arxiv.oai.paper;

import com.youtube.arxiv.oai.record.ArxivRecordEntity;
import com.youtube.arxiv.oai.section.ArxivSectionEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "raw_content", columnDefinition = "text")
    private String rawContent;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "keyword_list", columnDefinition = "text[]")
    private List<String> keywords;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "affiliation_list", columnDefinition = "text[]")
    private List<String> affiliations;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "reference_list", columnDefinition = "text[]")
    private List<String> references;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "class_code_list", columnDefinition = "text[]")
    private List<String> classCodes;

    @Column(name = "doc_type", columnDefinition = "text")
    private String docType;

    @OneToMany(
        mappedBy = "document",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderColumn(name = "pos")
    private List<ArxivSectionEntity> sections = new ArrayList<>();

    public void addSection(ArxivSectionEntity section) {
        sections.add(section);
        section.setDocument(this);
    }

    public void removeSection(ArxivSectionEntity section) {
        sections.remove(section);
        section.setDocument(null);
    }
}
