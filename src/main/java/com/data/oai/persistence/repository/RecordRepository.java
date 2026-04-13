package com.data.oai.persistence.repository;

import com.data.oai.persistence.entity.RecordEntity;
import com.data.shared.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecordRepository extends JpaRepository<RecordEntity, Long> {
    Optional<RecordEntity> findBySourceId(String arxivId);

    Optional<RecordEntity> findByExternalIdentifier(String externalIdentifier);

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

    /**
     * Streams every sourceId already persisted for the given data source.
     * Used by pipelines that dedupe at the source level and cannot rely on
     * a datestamp window (e.g. PMC S3, which processes a whole inventory manifest).
     */
    @Query("""
                SELECT r.sourceId
                FROM RecordEntity r
                WHERE r.dataSource = :dataSource
            """)
    List<String> findAllSourceIdsByDataSource(@Param("dataSource") DataSource dataSource);
}
