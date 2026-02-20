package com.data.oai.generic.common.author;

import com.data.oai.generic.common.record.RecordEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "record_author",
       indexes = @Index(name = "idx_record_author_record_id", columnList = "record_id"),
       uniqueConstraints = @UniqueConstraint(name = "uq_record_author_record_pos", columnNames = {"record_id", "pos"}))
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
        foreignKey = @ForeignKey(name = "fk_record_author_record")
    )
    private RecordEntity record;

    @Column(name = "first_name", length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(name = "pos", length = 128)
    private Integer pos;
}
