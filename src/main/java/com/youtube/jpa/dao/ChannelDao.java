package com.youtube.jpa.dao;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "channels")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChannelDao {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "youtube_channel_id", nullable = false, unique = true, length = 64)
  private String youtubeChannelId;

  @Column(nullable = false)
  private String name;

  @Column
  private String description;

  @Column
  private String country;
}
