package com.data.oai.common.record;

import com.data.oai.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecordRepository extends JpaRepository<RecordEntity, Long> {
    Optional<RecordEntity> findByArxivId(String arxivId);
    Optional<RecordEntity> findByOaiIdentifier(String oaiIdentifier);
    boolean existsByArxivId(String arxivId);

    @Query("""
        select r.arxivId
        from RecordEntity r
        where r.datestamp >= :start and r.datestamp < :end and r.dataSource = :dataSource
    """)
    List<String> findArxivIdsProcessedInPeriodAndByDataSource(@Param("start") LocalDate start,
                                               @Param("end") LocalDate end, DataSource dataSource);
}
