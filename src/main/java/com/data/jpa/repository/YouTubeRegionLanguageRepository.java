package com.data.jpa.repository;

import com.data.jpa.dao.YouTubeRegionLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YouTubeRegionLanguageRepository extends JpaRepository<YouTubeRegionLanguage, Long> {
    List<YouTubeRegionLanguage> findByRegionRegionIdOrderByPriorityAsc(String regionId);

    List<YouTubeRegionLanguage> findByLanguageCode(String language);
}
