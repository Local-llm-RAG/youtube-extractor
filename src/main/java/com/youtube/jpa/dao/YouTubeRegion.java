package com.youtube.jpa.dao;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "youtube_regions",
        uniqueConstraints = @UniqueConstraint(name = "uk_youtube_regions_region_id", columnNames = "region_id")
)
public class YouTubeRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "region_id", nullable = false, length = 64, unique = true)
    private String regionId;

    @Column(name = "country_name", nullable = false, length = 64)
    private String countryName;

    @OneToMany(mappedBy = "region", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC")
    private List<YouTubeRegionLanguage> preferredLanguages = new ArrayList<>();

    public void replacePreferredLanguages(List<String> codes) {
        List<String> desired = new ArrayList<>(new LinkedHashSet<>(codes));

        Map<String, YouTubeRegionLanguage> existingByCode = this.preferredLanguages.stream()
                .collect(Collectors.toMap(YouTubeRegionLanguage::getLanguageCode, x -> x));

        this.preferredLanguages.removeIf(pl -> !desired.contains(pl.getLanguageCode()));

        int prio = 0;
        for (String code : desired) {
            YouTubeRegionLanguage child = existingByCode.get(code);
            if (child == null) {
                child = new YouTubeRegionLanguage();
                child.setRegion(this);
                child.setLanguageCode(code);
                this.preferredLanguages.add(child);
            }
            child.setPriority(prio++);
        }
    }
}
