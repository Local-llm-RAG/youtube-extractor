package com.data.oai.persistence.repository;

import com.data.oai.persistence.entity.RecordEntity;
import com.data.shared.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
     * Returns record IDs for a given data source, ordered by created_at for deterministic pagination.
     * Used by S3 export in FULL mode (all records).
     */
    @Query("""
                SELECT r.id FROM RecordEntity r
                WHERE r.dataSource = :dataSource
                ORDER BY r.createdAt ASC
            """)
    List<Long> findIdsByDataSource(@Param("dataSource") DataSource dataSource, Pageable pageable);

    /**
     * Returns record IDs created after the given watermark timestamp, for incremental export.
     */
    @Query("""
                SELECT r.id FROM RecordEntity r
                WHERE r.dataSource = :dataSource AND r.createdAt > :after
                ORDER BY r.createdAt ASC
            """)
    List<Long> findIdsByDataSourceAndCreatedAfter(@Param("dataSource") DataSource dataSource,
                                                   @Param("after") OffsetDateTime after,
                                                   Pageable pageable);

    /**
     * Returns record IDs for a given data source filtered by datestamp range, for FULL mode date-range export.
     * Null-safe: if from/to are null, callers should fall back to {@link #findIdsByDataSource}.
     */
    @Query("""
                SELECT r.id FROM RecordEntity r
                WHERE r.dataSource = :dataSource
                  AND r.datestamp >= :from
                  AND r.datestamp <= :to
                ORDER BY r.createdAt ASC
            """)
    List<Long> findIdsByDataSourceAndDatestampBetween(@Param("dataSource") DataSource dataSource,
                                                       @Param("from") LocalDate from,
                                                       @Param("to") LocalDate to,
                                                       Pageable pageable);

    /**
     * Fetches records with their document eagerly loaded, for a given set of IDs.
     * Sections, references, authors, and chunks are loaded separately to avoid Cartesian product.
     */
    @Query("""
                SELECT r FROM RecordEntity r
                JOIN FETCH r.document
                WHERE r.id IN :ids
                ORDER BY r.createdAt ASC
            """)
    List<RecordEntity> findByIdsWithDocument(@Param("ids") List<Long> ids);

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
