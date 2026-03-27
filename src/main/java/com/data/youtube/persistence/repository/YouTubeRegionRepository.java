package com.data.youtube.persistence.repository;

import com.data.youtube.persistence.entity.YouTubeRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface YouTubeRegionRepository extends JpaRepository<YouTubeRegion, Long> {

    Optional<YouTubeRegion> findByRegionId(String regionId);

    @Query("select distinct r from YouTubeRegion r left join fetch r.preferredLanguages")
    List<YouTubeRegion> findAllWithPreferredLanguages();

    @Query("""
        select distinct l.languageCode
        from YouTubeRegion r
        join r.preferredLanguages l
        order by l.languageCode
    """)
    List<String> findDistinctPreferredLanguageCodes();
}
