package com.youtube.arxiv.oai.record;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ArxivRecordRepository extends JpaRepository<ArxivRecordEntity, Long> {
    Optional<ArxivRecordEntity> findByArxivId(String arxivId);
    Optional<ArxivRecordEntity> findByOaiIdentifier(String oaiIdentifier);
    boolean existsByArxivId(String arxivId);

    @Query("""
        select r.arxivId
        from ArxivRecordEntity r
        where r.datestamp >= :start and r.datestamp < :end
    """)
    List<String> findArxivIdsProcessedInPeriod(@Param("start") LocalDate start,
                                               @Param("end") LocalDate end);
}
