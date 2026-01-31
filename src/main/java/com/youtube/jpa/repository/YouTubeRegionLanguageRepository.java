package com.youtube.jpa.repository;

import com.youtube.jpa.dao.YouTubeRegionLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YouTubeRegionLanguageRepository extends JpaRepository<YouTubeRegionLanguage, Long> {
    List<YouTubeRegionLanguage> findByRegionRegionIdOrderByPriorityAsc(String regionId);
}
