package com.youtube.jpa.dao;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "videos")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Video {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "youtube_video_id", nullable = false, unique = true, length = 64)
  private String youtubeVideoId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "channel_id", nullable = false)
  private ChannelDao channelDao;

  @Column(name = "transcript_passed", nullable = false)
  private boolean transcriptPassed;

  // --- SNIPPET ---
  @Column(name = "title")
  private String title;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @Column(name = "category_id", length = 32)
  private String categoryId;

  @Column(name = "category_title", length = 128)
  private String categoryTitle;

  @Column(name = "default_language", length = 32)
  private String defaultLanguage;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "tags", columnDefinition = "text[]")
  private List<String> tags;

  // --- STATUS ---
  @Column(name = "made_for_kids")
  private Boolean madeForKids;

  @Column(name = "license", length = 32)
  private String license;

  // --- TOPIC DETAILS ---
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "topic_categories", columnDefinition = "text[]")
  private List<String> topicCategories;
}
