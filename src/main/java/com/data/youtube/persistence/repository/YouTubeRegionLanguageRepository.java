package com.data.youtube.persistence.repository;

import com.data.youtube.persistence.entity.YouTubeRegionLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YouTubeRegionLanguageRepository extends JpaRepository<YouTubeRegionLanguage, Long> {
    List<YouTubeRegionLanguage> findByRegionRegionIdOrderByPriorityAsc(String regionId);

    List<YouTubeRegionLanguage> findByLanguageCode(String language);
}
