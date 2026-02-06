package com.youtube.service.youtube;

import com.google.api.services.youtube.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YouTubeClientService {

    public static final List<String> VIDEO_RETURNED_FIELDS = List.of("snippet", "statistics", "status", "topicDetails");
    public static final List<String> PLAYLIST_RETURNED_FIELDS = List.of("snippet", "contentDetails");
    public static final int BATCH_SIZE = 50;
    public static final List<String> CATEGORIES_RETURNED_FIELDS = List.of("snippet");
    private final YouTubeGateway gateway;

    public Channel fetchChannelByChannelHandle(String channelId) throws Exception {
        ChannelListResponse resp = gateway.listChannelsByHandle(channelId);

        return Optional.ofNullable(resp.getItems())
                .filter(items -> !items.isEmpty())
                .map(List::getFirst)
                .orElseThrow(() -> new IllegalArgumentException("No channel found for handle: " + channelId));
    }

    public Channel fetchChannelByChannelId(String channelId) throws Exception {
        ChannelListResponse resp = gateway.listChannelsById(channelId);

        return Optional.ofNullable(resp.getItems())
                .filter(items -> !items.isEmpty())
                .map(List::getFirst)
                .orElseThrow(() -> new IllegalArgumentException("No channel found for handle: " + channelId));
    }

    public List<String> fetchUniqueVideoIdsFromUploadsPlaylist(
            String uploadsPlaylistId,
            Instant startInclusive,
            Instant endExclusive
    ) throws Exception {

        Objects.requireNonNull(uploadsPlaylistId, "uploadsPlaylistId");

        List<String> result = new ArrayList<>();
        String pageToken = null;
        AtomicBoolean stop = new AtomicBoolean(false);

        do {
            PlaylistItemListResponse plResp =
                    gateway.listPlaylistItemsPage(uploadsPlaylistId, pageToken, BATCH_SIZE, PLAYLIST_RETURNED_FIELDS);

            Optional.ofNullable(plResp.getItems()).
                    orElseGet(List::of)
                    .stream()
                    .filter(Objects::nonNull)
                    .peek(item -> {
                        if (startInclusive == null) return;

                        PlaylistItemSnippet snippet = item.getSnippet();
                        if (snippet == null || snippet.getPublishedAt() == null) return;

                        Instant publishedAt = Instant.ofEpochMilli(snippet.getPublishedAt().getValue());
                        if (publishedAt.isBefore(startInclusive)) {
                            stop.set(true);
                        }
                    })
                    .takeWhile(_ -> !stop.get())
                    .filter(item -> item.getSnippet() != null && item.getSnippet().getPublishedAt() != null)
                    .filter(item -> {
                        Instant publishedAt = Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue());
                        if (startInclusive != null && publishedAt.isBefore(startInclusive)) return false;
                        if (endExclusive != null && publishedAt.isAfter(endExclusive)) return false;
                        return true;
                    })
                    .map(PlaylistItem::getContentDetails)
                    .filter(Objects::nonNull)
                    .map(PlaylistItemContentDetails::getVideoId)
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .forEach(result::add);
            pageToken = plResp.getNextPageToken();
        } while (!stop.get() && pageToken != null && !pageToken.isBlank());

        return result.stream().distinct().toList();
    }

    public Map<String, String> fetchCategoryIdToTitle(String regionCode) {
        VideoCategoryListResponse resp = null;
        try {
            resp = gateway.listVideoCategories(regionCode, CATEGORIES_RETURNED_FIELDS);
        } catch (Exception e) {
            throw new RuntimeException("listVideoCategories call throws exception", e);
        }
        return Optional.ofNullable(resp.getItems()).orElseGet(List::of).stream()
                .collect(Collectors.toMap(VideoCategory::getId, c -> c.getSnippet().getTitle()));
    }

    public List<com.google.api.services.youtube.model.Video> fetchVideosDetailsBatched(List<String> videoIds) throws Exception {
        List<com.google.api.services.youtube.model.Video> out = new ArrayList<>();

        for (int i = 0; i < videoIds.size(); i += BATCH_SIZE) {
            List<String> batch = videoIds.subList(i, Math.min(i + BATCH_SIZE, videoIds.size()));
            VideoListResponse resp = gateway.listVideosByIds(batch, VIDEO_RETURNED_FIELDS);
            out.addAll(Optional.ofNullable(resp.getItems()).orElseGet(List::of));
        }
        return List.copyOf(out);
    }
}
