package com.youtube.service.youtube;

import com.google.api.services.youtube.model.Channel;
import com.youtube.jpa.dao.ChannelDao;
import com.youtube.jpa.dao.Video;
import com.youtube.service.event.VideoDiscoveredEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeChannelVideosService {

    private final YouTubeClientService youtubeClientService;
    private final YouTubeInternalService youtubeInternalService;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public List<String> fetchAndSaveAllVideoIdsByMultipleHandles(
            List<String> channelUrls,
            YoutubeTranscriptFetchStrategy fetchStrategy,
            List<String> desiredLanguages,
            LocalDate optionalStartDate,
            LocalDate optionalEndDate
    ) {
        return channelUrls.stream().flatMap(channelHandle -> {
            try {
                return fetchAndSaveAllVideoIdsByHandle(
                        channelHandle,
                        fetchStrategy,
                        desiredLanguages,
                        optionalStartDate,
                        optionalEndDate
                ).stream();
            } catch (Exception e) {
                log.error("Something went wrong with channelHandle {}", channelHandle);
                return Stream.of();
            }
        }).toList();
    }

    @Transactional
    public List<String> fetchAndSaveAllVideoIdsByHandle(
            String fullChannelUrl,
            YoutubeTranscriptFetchStrategy fetchStrategy,
            List<String> desiredLanguages,
            LocalDate optionalStartDate,
            LocalDate optionalEndDate
    ) throws Exception {
        Channel ytChannel = youtubeClientService.fetchChannelByChannelHandle(normalizeHandle(fullChannelUrl));
        String uploadsPlaylistId = ytChannel.getContentDetails()
                .getRelatedPlaylists()
                .getUploads();
        log.info("Collected channel with url {} with upload playlist id {}", fullChannelUrl, uploadsPlaylistId);
        List<String> uniqueVideoIds = youtubeClientService
                .fetchUniqueVideoIdsFromUploadsPlaylist(uploadsPlaylistId, optionalStartDate, optionalEndDate);

        log.info("Unique video ids {}", uniqueVideoIds);
        Map<String, Map<String, String>> categoryTitlesByLanguage = categoriesPerLanguage(desiredLanguages);
        if (uniqueVideoIds.isEmpty()) {
            log.info("No videos for channel collected");
            return persistAndPublish(ytChannel, categoryTitlesByLanguage, List.of(), fetchStrategy, desiredLanguages);
        }

        List<com.google.api.services.youtube.model.Video> ytVideos =
                youtubeClientService.fetchVideosDetailsBatched(uniqueVideoIds);
        log.info("Video details successfully collected");
        // 2) DB work (TX)
        return persistAndPublish(ytChannel, categoryTitlesByLanguage, ytVideos, fetchStrategy, desiredLanguages);
    }

    @Transactional
    public void fetchAndSaveVideoByUrl(String videoUrl, List<String> desiredLanguages) throws Exception {
        AbstractMap.SimpleEntry<Channel, com.google.api.services.youtube.model.Video> channelWithVideo = getYoutubeVideoWithChannel(videoUrl);
        Map<String, Map<String, String>> categoryTitlesByLanguage = categoriesPerLanguage(desiredLanguages);
        persistAndPublish(channelWithVideo.getKey(), categoryTitlesByLanguage, channelWithVideo.getValue(), desiredLanguages);
    }

    private List<String> persistAndPublish(Channel ytChannel,
                                           Map<String, Map<String, String>> categoryMap,
                                           List<com.google.api.services.youtube.model.Video> allVideos,
                                           YoutubeTranscriptFetchStrategy fetchStrategy,
                                           List<String> desiredLanguages) {
        ChannelDao channel = youtubeInternalService.upsertChannelFromYouTube(ytChannel);

        List<Video> insertedVideos = youtubeInternalService.insertMissingVideos(channel, allVideos, categoryMap);

        // TODO: For minor optimization could upsert only videos that are already inserted. (ALL - inserted)
        // TODO: Also can combine in one function insert + upsert logic
        youtubeInternalService.upsertVideoDetails(channel, allVideos, categoryMap);

        Long channelDbId = channel.getId();
        if (fetchStrategy == YoutubeTranscriptFetchStrategy.FOR_FAILED) {
            youtubeInternalService.findAllVideosForChannelWithCategories(channel, categoryMap, allVideos)
                    .stream()
                    .filter(video -> !video.isTranscriptPassed())
                    .forEach(video -> publisher.publishEvent(new VideoDiscoveredEvent(video.getYoutubeVideoId(), channelDbId, desiredLanguages, video.getCategoriesEntry())));
        } else if (fetchStrategy == YoutubeTranscriptFetchStrategy.FOR_ALL) {
            List<Video> allVideosFromDBWithCategories = youtubeInternalService.findAllVideosForChannelWithCategories(channel, categoryMap, allVideos);
            allVideosFromDBWithCategories.forEach(video -> publisher.publishEvent(new VideoDiscoveredEvent(video.getYoutubeVideoId(), channelDbId, desiredLanguages, video.getCategoriesEntry())));
        } else if (fetchStrategy == YoutubeTranscriptFetchStrategy.FOR_NEWEST) {
            insertedVideos.forEach(video -> publisher.publishEvent(new VideoDiscoveredEvent(video.getYoutubeVideoId(), channelDbId, desiredLanguages, video.getCategoriesEntry())));
        }
        return allVideos.stream().map(com.google.api.services.youtube.model.Video::getId).toList();
    }

    private void persistAndPublish(Channel ytChannel,
                                   Map<String, Map<String, String>> categoryMap,
                                   com.google.api.services.youtube.model.Video video,
                                   List<String> desiredLanguages) {
        ChannelDao channel = youtubeInternalService.upsertChannelFromYouTube(ytChannel);

        Video insertedVideo = youtubeInternalService.upsertVideoDetails(channel, List.of(video), categoryMap)
                .stream()
                .findFirst()
                .orElseThrow();

        Long channelDbId = channel.getId();
        publisher.publishEvent(new VideoDiscoveredEvent(video.getId(), channelDbId, desiredLanguages, insertedVideo.getCategoriesEntry()));
    }

    public AbstractMap.SimpleEntry<Channel, com.google.api.services.youtube.model.Video> getYoutubeVideoWithChannel(String videoUrl) throws Exception {
        String videoId = extractVideoId(videoUrl);

        com.google.api.services.youtube.model.Video youtubeVideo = youtubeClientService.fetchVideosDetailsBatched(List.of(videoId))
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cannot find video with url %s".formatted(videoUrl)));

        String channelId = youtubeVideo.getSnippet().getChannelId();
        Channel youtubeChannel = youtubeClientService.fetchChannelByChannelId(channelId);
        return new AbstractMap.SimpleEntry<>(youtubeChannel, youtubeVideo);
    }

    private String normalizeHandle(String link) {
        if (link == null || link.isBlank()) {
            throw new IllegalArgumentException("Handle is required");
        }

        String h = link.trim();
        int at = h.indexOf("@");
        if (at >= 0) h = h.substring(at);
        if (!h.startsWith("@")) h = "@" + h;
        return h;
    }

    public static String extractVideoId(String url) {
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            String path = uri.getPath() == null ? "" : uri.getPath();

            // youtube.com/watch?v=<id>
            if (host.contains("youtube.com")) {
                String query = uri.getQuery();
                if (query != null) {
                    for (String kv : query.split("&")) {
                        int idx = kv.indexOf('=');
                        if (idx > 0 && "v".equals(kv.substring(0, idx))) {
                            String v = kv.substring(idx + 1);
                            if (v.length() >= 11) return v.substring(0, 11);
                        }
                    }
                }
            }
            return path;
        } catch (Exception ignored) {
            log.error("Cannot extract video with url {}", url);
        }
        throw new IllegalArgumentException("Could not extract videoId from URL: " + url);
    }

    private Map<String, Map<String, String>> categoriesPerLanguage(List<String> desiredLanguages) {
        return desiredLanguages.stream()
                .collect(toMap(
                        language -> language,
                        language -> youtubeInternalService.fetchRegionsForLanguage(language).stream()
                                .flatMap(region -> youtubeClientService.fetchCategoryIdToTitle(region).entrySet().stream())
                                .collect(toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (existing, replacement) -> existing
                                ))
                ));
    }
}
