package com.data.jpa.repository;

import com.data.jpa.dao.ChannelDao;
import com.data.jpa.dao.Video;
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

  List<Video> findVideosByChannelDao(ChannelDao channel);

  @Query("""
    SELECT DISTINCT v
    FROM Video v
    LEFT JOIN FETCH v.transcripts t
    WHERE v.channelDao.youtubeChannelId = :youtubeChannelId
""")
  List<Video> findAllByYoutubeChannelWithTranscripts(@Param("youtubeChannelId") String youtubeChannelId);
}
