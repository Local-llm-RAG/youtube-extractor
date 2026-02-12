package com.youtube.jpa.repository;

import com.youtube.jpa.dao.ArxivTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArxivTrackerRepository extends JpaRepository<ArxivTracker, Long> {

    Optional<ArxivTracker> findTopByOrderByDateEndDesc();
}
