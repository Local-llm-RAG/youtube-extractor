package com.youtube.service;

import com.google.api.services.youtube.model.Channel;
import com.youtube.jpa.dao.ChannelDao;
import com.youtube.jpa.dao.Video;
import com.youtube.service.event.VideoDiscoveredEvent;
import com.youtube.service.test.YouTubeClientService;
import com.youtube.service.test.YouTubeInternalService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class YouTubeChannelVideosService {

    private final YouTubeClientService youtubeClientService;
    private final YouTubeInternalService youtubeInternalService;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public List<String> fetchAndSaveAllVideoIdsByHandle(String fullChannelUrl, Boolean runTranscriptsSavingForAll) throws Exception {
        Channel ytChannel = youtubeClientService.fetchChannelByChannelUrl(fullChannelUrl);
        String uploadsPlaylistId = ytChannel.getContentDetails()
                .getRelatedPlaylists()
                .getUploads();

        List<String> uniqueVideoIds = youtubeClientService
                .fetchAllUniqueVideoIdsFromPlaylist(uploadsPlaylistId);

        // TODO: when import other channels this should not be hardcoded
        Map<String, String> categoryMap = youtubeClientService.fetchCategoryIdToTitle("BG");
        if(uniqueVideoIds.isEmpty()) {
            return persistAndPublish(ytChannel, categoryMap, List.of(), runTranscriptsSavingForAll);
        }

        List<com.google.api.services.youtube.model.Video> ytVideos =
                youtubeClientService.fetchVideosDetailsBatched(uniqueVideoIds);
        // 2) DB work (TX)
        return persistAndPublish(ytChannel, categoryMap, ytVideos, runTranscriptsSavingForAll);
    }

    private List<String> persistAndPublish(Channel ytChannel,
                                             Map<String, String> categoryMap,
                                             List<com.google.api.services.youtube.model.Video> allVideos,
                                             Boolean runTranscriptsSavingForAll
    ) {
        ChannelDao channel = youtubeInternalService.upsertChannelFromYouTube(ytChannel);

        List<Video> insertedVideos = youtubeInternalService.insertMissingVideos(channel, allVideos, categoryMap);

        // TODO: For minor optimization could upsert only videos that are already inserted. (ALL - inserted)
        // TODO: Also can combine in one function insert + upsert logic
        youtubeInternalService.upsertVideoDetails(channel, allVideos, categoryMap);

        Long channelDbId = channel.getId();

        if(runTranscriptsSavingForAll) {
            allVideos.forEach(video -> publisher.publishEvent(new VideoDiscoveredEvent(video.getId(), channelDbId)));
        } else {
            insertedVideos.forEach(video -> publisher.publishEvent(new VideoDiscoveredEvent(video.getYoutubeVideoId(), channelDbId)));
        }

        return allVideos.stream().map(com.google.api.services.youtube.model.Video::getId).toList();
    }
}
