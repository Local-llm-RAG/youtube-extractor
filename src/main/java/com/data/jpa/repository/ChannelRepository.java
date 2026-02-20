package com.data.jpa.repository;

import com.data.jpa.dao.ChannelDao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<ChannelDao, Long> {
  Optional<ChannelDao> findByYoutubeChannelId(String youtubeChannelId);
}
