package com.youtube.startup;

import com.google.api.services.youtube.model.I18nLanguage;
import com.google.api.services.youtube.model.I18nLanguageListResponse;
import com.google.api.services.youtube.model.I18nRegion;
import com.google.api.services.youtube.model.I18nRegionListResponse;
import com.youtube.jpa.dao.YouTubeRegion;
import com.youtube.jpa.repository.YouTubeRegionRepository;
import com.youtube.service.youtube.YouTubeGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionBootstrapService {

    private final YouTubeGateway gateway;
    private final YouTubeRegionRepository repo;

    @Transactional
    public void importRegionsOnce() throws Exception {
        var regionsResponse = gateway.getAllRegions();
        var youtubeLangIds = gateway.getAllLanguages().getItems().stream()
                .map(I18nLanguage::getId)
                .collect(Collectors.toSet());

        Map<String, YouTubeRegion> existing = repo.findAllWithPreferredLanguages().stream()
                .collect(Collectors.toMap(YouTubeRegion::getRegionId, r -> r));

        List<YouTubeRegion> newOnes = new ArrayList<>();

        for (var region : regionsResponse.getItems()) {
            String regionGl = region.getSnippet().getGl();
            String countryName = region.getSnippet().getName();
            List<String> preferred = preferredForRegion(regionGl, youtubeLangIds);

            YouTubeRegion entity = existing.get(regionGl);

            if (entity == null) {
                entity = new YouTubeRegion();
                entity.setRegionId(regionGl);
                entity.setCountryName(countryName);
                entity.replacePreferredLanguages(preferred);
                newOnes.add(entity);
            } else {
                entity.setCountryName(countryName);
                entity.replacePreferredLanguages(preferred);
            }
        }

        if (!newOnes.isEmpty()) {
            repo.saveAll(newOnes);
        }
    }
    static List<String> preferredForRegion(String regionGl, Set<String> youtubeLangIds) {
        if (regionGl == null || regionGl.isBlank()) {
            return fallback(youtubeLangIds);
        }

        String region = regionGl.toUpperCase(Locale.ROOT);

        List<Locale> localesForCountry = Arrays.stream(Locale.getAvailableLocales())
                .filter(l -> region.equalsIgnoreCase(l.getCountry()))
                .toList();

        LinkedHashSet<String> result = new LinkedHashSet<>();

        for (Locale l : localesForCountry) {
            String lang = normalizeLanguage(l.getLanguage());
            if (lang != null && youtubeLangIds.contains(lang)) {
                result.add(lang);
            }
        }

        for (Locale l : localesForCountry) {
            String tag = normalizeTag(l);
            if (tag != null && youtubeLangIds.contains(tag)) {
                result.add(tag);
            }
        }

        if (result.isEmpty()) {
            return fallback(youtubeLangIds);
        }

        return List.copyOf(result);
    }
    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) return null;

        if ("he".equalsIgnoreCase(language)) return "iw";

        return language.toLowerCase(Locale.ROOT);
    }

    private static String normalizeTag(Locale l) {
        if (l == null) return null;

        String tag = l.toLanguageTag();
        if (tag.isBlank()) return null;

        if (tag.startsWith("he")) return "iw";

        return tag;
    }

    private static List<String> fallback(Set<String> youtubeLangIds) {
        if (youtubeLangIds.contains("en")) {
            return List.of("en");
        }
        return youtubeLangIds.stream().findFirst().map(List::of).orElse(List.of());
    }
}
