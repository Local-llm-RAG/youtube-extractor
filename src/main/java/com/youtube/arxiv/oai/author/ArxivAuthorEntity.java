package com.youtube.arxiv.oai.author;

import com.youtube.arxiv.oai.record.ArxivRecordEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "arxiv_author",
       indexes = @Index(name = "idx_arxiv_author_record_id", columnList = "record_id"),
       uniqueConstraints = @UniqueConstraint(name = "uq_author_record_pos", columnNames = {"record_id", "pos"}))
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArxivAuthorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
        name = "record_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_author_record")
    )
    private ArxivRecordEntity record;

    @Column(name = "first_name", length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(name = "pos", length = 128)
    private Integer pos;
}
