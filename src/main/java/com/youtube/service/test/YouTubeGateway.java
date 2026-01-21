package com.youtube.service.test;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.VideoCategoryListResponse;
import com.google.api.services.youtube.model.VideoListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class YouTubeGateway {

    private final YouTube youtube;

    @Value("${youtube.api-key}")
    private String apiKey;

    public ChannelListResponse listChannelsByHandle(String handle) throws Exception {
        return youtube.channels()
                .list(List.of("id", "contentDetails", "snippet"))
                .setForHandle(handle)
                .setKey(apiKey)
                .execute();
    }

    public PlaylistItemListResponse listPlaylistItemsPage(String playlistId, String pageToken, long maxResults) throws Exception {
        return youtube.playlistItems()
                .list(List.of("contentDetails"))
                .setPlaylistId(playlistId)
                .setMaxResults(maxResults)
                .setPageToken(pageToken)
                .setKey(apiKey)
                .execute();
    }

    public VideoListResponse listVideosByIds(List<String> ids, List<String> parts) throws Exception {
        return youtube.videos()
                .list(parts)
                .setId(ids)
                .setKey(apiKey)
                .execute();
    }

    public VideoCategoryListResponse listVideoCategories(String regionCode, List<String> parts) throws Exception {
        return youtube.videoCategories()
                .list(parts)
                .setRegionCode(regionCode)
                .setKey(apiKey)
                .execute();
    }
}
