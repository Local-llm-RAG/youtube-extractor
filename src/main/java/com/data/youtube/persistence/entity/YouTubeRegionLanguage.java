package com.data.youtube.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "youtube_region_languages",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_region_language",
        columnNames = {"youtube_region_id", "language_code"}
    )
)
public class YouTubeRegionLanguage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "youtube_region_languages_seq")
    @SequenceGenerator(name = "youtube_region_languages_seq", sequenceName = "youtube_region_languages_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "youtube_region_id", nullable = false)
    private YouTubeRegion region;

    @Column(name = "language_code", nullable = false, length = 64)
    private String languageCode;

    @Column(name = "priority", nullable = false)
    private int priority;
}
