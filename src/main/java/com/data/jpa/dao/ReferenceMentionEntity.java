package com.data.jpa.dao;

import com.data.oai.generic.common.paper.PaperDocumentEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Data
@Entity
@Table(
    name = "reference_mention",
    indexes = {
        @Index(name = "idx_reference_record_id", columnList = "record_document_id"),
        @Index(name = "idx_reference_doi", columnList = "doi")
    }
)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceMentionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "record_document_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_reference_record")
    )
    private PaperDocumentEntity document;

    @Column(name = "ref_index", nullable = false)
    private Integer refIndex;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "doi", columnDefinition = "text")
    private String doi;

    @Column(name = "year", length = 10)
    private String year;

    @Column(name = "venue", columnDefinition = "text")
    private String venue;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "authors", columnDefinition = "text[]")
    private List<String> authors;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "urls", columnDefinition = "text[]")
    private List<String> urls;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "idnos", columnDefinition = "text[]")
    private List<String> idnos;
}