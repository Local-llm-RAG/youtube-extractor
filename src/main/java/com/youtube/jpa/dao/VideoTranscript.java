package com.youtube.jpa.dao;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "video_transcripts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many transcripts can belong to one video
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false) // removed unique=true
    private Video video;

    @Column(name = "transcript_text", nullable = false, columnDefinition = "text")
    private String transcriptText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "category_id", columnDefinition = "text[]")
    private List<String> categoryIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "category_title", columnDefinition = "text[]")
    private List<String> categoryTitles;

    @Column(name = "language")
    private String language;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
