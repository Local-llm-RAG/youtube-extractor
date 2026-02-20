package com.data.oai.common.paper;

import com.data.oai.common.record.RecordEntity;
import com.data.oai.common.section.SectionEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
    name = "record_document",
    indexes = {
        @Index(name = "idx_record_document_record_id", columnList = "record_id"),
    },
    uniqueConstraints = @UniqueConstraint(name = "uq_record_document_record", columnNames = "record_id")
)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
        name = "record_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_record_document_record")
    )
    private RecordEntity record;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "abstract", columnDefinition = "text")
    private String abstractText;

    @Column(name = "tei_xml", columnDefinition = "text")
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
    private List<SectionEntity> sections = new ArrayList<>();

    public void addSection(SectionEntity section) {
        sections.add(section);
        section.setDocument(this);
    }

    public void removeSection(SectionEntity section) {
        sections.remove(section);
        section.setDocument(null);
    }
}
