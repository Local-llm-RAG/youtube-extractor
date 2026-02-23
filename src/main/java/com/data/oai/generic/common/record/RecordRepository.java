package com.data.oai.generic.common.record;

import com.data.oai.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecordRepository extends JpaRepository<RecordEntity, Long> {
    Optional<RecordEntity> findBySourceId(String arxivId);

    Optional<RecordEntity> findByOaiIdentifier(String oaiIdentifier);

    boolean existsBySourceId(String arxivId);

    @Query("""
                select r.sourceId
                from RecordEntity r
                where r.datestamp >= :start and r.datestamp < :end and r.dataSource = :dataSource
            """)
    List<String> findSourceIdsProcessedInPeriodAndByDataSource(@Param("start") LocalDate start,
                                                               @Param("end") LocalDate end,
                                                               DataSource dataSource);

    @Query("""
                SELECT r.sourceId
                FROM RecordEntity r
                WHERE r.sourceId in :ids AND r.dataSource = :dataSource
            """)
    List<String> findExistingArxivIds(@Param("ids") List<String> ids,
                                      @Param("dataSource") DataSource dataSource);
}
