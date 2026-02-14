package com.youtube.arxiv.oai.tracker;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "arxiv_tracker",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tracker_period", columnNames = {"date_start", "date_end"})
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArxivTracker {

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
}
