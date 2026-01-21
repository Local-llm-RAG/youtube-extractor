package com.youtube.service.test;

import com.google.api.services.youtube.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YouTubeClientService {

    public static final List<String> VIDEO_RETURNED_FIELDS = List.of("snippet", "statistics", "status", "topicDetails");
    public static final int BATCH_SIZE = 50;
    public static final List<String> CATEGORIES_RETURNED_FIELDS = List.of("snippet");
    private final YouTubeGateway gateway;

    public String normalizeHandle(String link) {
        if (link == null || link.isBlank()) {
            throw new IllegalArgumentException("Handle is required");
        }

        String h = link.trim();
        int at = h.indexOf("@");
        if (at >= 0) h = h.substring(at);
        if (!h.startsWith("@")) h = "@" + h;
        return h;
    }

    public Channel fetchChannelByChannelUrl(String handle) throws Exception {
        String normalizedHandle = normalizeHandle(handle);
        ChannelListResponse resp = gateway.listChannelsByHandle(normalizedHandle);

        return Optional.ofNullable(resp.getItems())
                .filter(items -> !items.isEmpty())
                .map(List::getFirst)
                .orElseThrow(() -> new IllegalArgumentException("No channel found for handle: " + normalizedHandle));
    }

    public List<String> fetchAllUniqueVideoIdsFromPlaylist(String playlistId) throws Exception {
        List<String> result = new ArrayList<>();
        String pageToken = null;

        do {
            PlaylistItemListResponse plResp = gateway.listPlaylistItemsPage(playlistId, pageToken, BATCH_SIZE);

            Optional.ofNullable(plResp.getItems()).orElseGet(List::of).stream()
                    .map(PlaylistItem::getContentDetails)
                    .filter(Objects::nonNull)
                    .map(PlaylistItemContentDetails::getVideoId)
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .forEach(result::add);

            pageToken = plResp.getNextPageToken();
        } while (pageToken != null && !pageToken.isBlank());

        return result.stream().distinct().toList();
    }

    public Map<String, String> fetchCategoryIdToTitle(String regionCode) throws Exception {
        VideoCategoryListResponse resp = gateway.listVideoCategories(regionCode, CATEGORIES_RETURNED_FIELDS);
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
