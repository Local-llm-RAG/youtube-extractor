package com.data.oai.generic.common.tracker;

import com.data.oai.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TrackerRepository extends JpaRepository<Tracker, Long> {

    Optional<Tracker> findTopByOrderByDateEndDesc();

    Optional<Tracker> findByDateStartAndDataSource(LocalDate dateStart, DataSource dataSource);

    @Modifying
    @Query("update Tracker t set t.processedPapersForPeriod = t.processedPapersForPeriod + 1 where t.id = :id")
    int incrementProcessed(@Param("id") Long id);
}
