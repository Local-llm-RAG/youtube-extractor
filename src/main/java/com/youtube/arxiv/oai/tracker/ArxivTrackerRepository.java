package com.youtube.arxiv.oai.tracker;

import com.youtube.arxiv.oai.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ArxivTrackerRepository extends JpaRepository<ArxivTracker, Long> {

    Optional<ArxivTracker> findTopByOrderByDateEndDesc();

    Optional<ArxivTracker> findByDateStartAndDataSource(LocalDate dateStart, DataSource dataSource);

    @Modifying
    @Query("update ArxivTracker t set t.processedPapersForPeriod = t.processedPapersForPeriod + 1 where t.id = :id")
    int incrementProcessed(@Param("id") Long id);
}
