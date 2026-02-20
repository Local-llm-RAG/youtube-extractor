package com.data.jpa.repository;

import com.data.jpa.dao.VideoTranscript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoTranscriptRepository extends JpaRepository<VideoTranscript, Long> {
    Optional<VideoTranscript> findByVideoId(Long videoId);
    boolean existsByVideoId(Long videoId);
}
