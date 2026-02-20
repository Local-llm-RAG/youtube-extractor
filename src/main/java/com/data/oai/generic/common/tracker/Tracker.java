package com.data.oai.generic.common.tracker;

import com.data.oai.DataSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "tracker",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tracker_period", columnNames = {"date_start", "date_end"})
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tracker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_start", nullable = false)
    private LocalDate dateStart;

    @Column(name = "date_end", nullable = false)
    private LocalDate dateEnd;

    @Column(name = "all_papers_for_period", nullable = false)
    private Integer allPapersForPeriod;

    @Column(name = "processed_papers_for_period", nullable = false)
    private Integer processedPapersForPeriod;

    @Column(name = "data_source", nullable = false)
    @Enumerated(EnumType.STRING)
    private DataSource dataSource;
}
