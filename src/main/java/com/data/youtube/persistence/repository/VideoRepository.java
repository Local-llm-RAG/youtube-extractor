package com.data.youtube.persistence.repository;

import com.data.youtube.persistence.entity.ChannelEntity;
import com.data.youtube.persistence.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

  boolean existsByYoutubeVideoId(String youtubeVideoId);

  List<Video> findAllByYoutubeVideoIdIn(Collection<String> youtubeVideoIds);

  Optional<Video> findByYoutubeVideoId(String youtubeVideoId);

  List<Video> findVideosByChannel(ChannelEntity channel);

  @Query("""
    SELECT DISTINCT v
    FROM Video v
    LEFT JOIN FETCH v.transcripts t
    WHERE v.channel.youtubeChannelId = :youtubeChannelId
""")
  List<Video> findAllByYoutubeChannelWithTranscripts(@Param("youtubeChannelId") String youtubeChannelId);
}
