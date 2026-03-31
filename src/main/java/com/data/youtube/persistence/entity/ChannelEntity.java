package com.data.youtube.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "channels")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChannelEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "channels_seq")
  @SequenceGenerator(name = "channels_seq", sequenceName = "channels_id_seq", allocationSize = 50)
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
