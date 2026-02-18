package com.youtube.arxiv.oai.tracker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ArxivTrackerRepository extends JpaRepository<ArxivTracker, Long> {

    Optional<ArxivTracker> findTopByOrderByDateEndDesc();

    Optional<ArxivTracker> findByDateStart(LocalDate dateStart);
}
