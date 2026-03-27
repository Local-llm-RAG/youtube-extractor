package com.data.youtube.persistence.repository;

import com.data.youtube.persistence.entity.VideoTranscript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoTranscriptRepository extends JpaRepository<VideoTranscript, Long> {
    Optional<VideoTranscript> findByVideoId(Long videoId);
    boolean existsByVideoId(Long videoId);
}
