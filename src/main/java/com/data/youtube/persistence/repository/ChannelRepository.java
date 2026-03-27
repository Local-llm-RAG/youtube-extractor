package com.data.youtube.persistence.repository;

import com.data.youtube.persistence.entity.ChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<ChannelEntity, Long> {
  Optional<ChannelEntity> findByYoutubeChannelId(String youtubeChannelId);
}
