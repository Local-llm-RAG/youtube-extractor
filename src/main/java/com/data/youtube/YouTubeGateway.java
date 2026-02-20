package com.data.youtube;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    public ChannelListResponse listChannelsById(String channelId) throws Exception {
        return youtube.channels()
                .list(List.of("id", "contentDetails", "snippet"))
                .setId(List.of(channelId))
                .setKey(apiKey)
                .execute();
    }

    public PlaylistItemListResponse listPlaylistItemsPage(String playlistId, String pageToken, long maxResults, List<String> parts) throws Exception {
        return youtube.playlistItems()
                .list(parts)
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

    public I18nRegionListResponse getAllRegions() throws IOException {
        return youtube.i18nRegions()
                .list(List.of("snippet"))
                .setKey(apiKey)
                .execute();

    }
    public I18nLanguageListResponse getAllLanguages() throws IOException {
        return youtube.i18nLanguages()
                .list(List.of("snippet"))
                .setKey(apiKey)
                .execute();

    }
}
