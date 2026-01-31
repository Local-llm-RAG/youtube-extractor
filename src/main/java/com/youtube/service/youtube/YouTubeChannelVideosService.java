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

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeChannelVideosService {

    private final YouTubeClientService youtubeClientService;
    private final YouTubeInternalService youtubeInternalService;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public List<String> fetchAndSaveAllVideoIdsByHandle(String fullChannelUrl, Boolean runTranscriptsSavingForAll, String regionCode) throws Exception {
        Channel ytChannel = youtubeClientService.fetchChannelByChannelUrl(fullChannelUrl);
        String uploadsPlaylistId = ytChannel.getContentDetails()
                .getRelatedPlaylists()
                .getUploads();
        log.info("Collected channel with url {} with upload playlist id {}", fullChannelUrl, uploadsPlaylistId);
        List<String> uniqueVideoIds = youtubeClientService
                .fetchAllUniqueVideoIdsFromPlaylist(uploadsPlaylistId);

        log.info("Unique video ids {}", uniqueVideoIds);
        // TODO: when import other channels this should not be hardcoded
        Map<String, String> categoryMap = youtubeClientService.fetchCategoryIdToTitle(regionCode);
        if (uniqueVideoIds.isEmpty()) {
            log.info("No videos for channel collected");
            return persistAndPublish(ytChannel, categoryMap, List.of(), runTranscriptsSavingForAll, regionCode);
        }

        List<com.google.api.services.youtube.model.Video> ytVideos =
                youtubeClientService.fetchVideosDetailsBatched(uniqueVideoIds);
        log.info("Video details successfully collected");
        // 2) DB work (TX)
        return persistAndPublish(ytChannel, categoryMap, ytVideos, runTranscriptsSavingForAll, regionCode);
    }

    private List<String> persistAndPublish(Channel ytChannel,
                                           Map<String, String> categoryMap,
                                           List<com.google.api.services.youtube.model.Video> allVideos,
                                           Boolean runTranscriptsSavingForAll,
                                           String regionCode) {
        ChannelDao channel = youtubeInternalService.upsertChannelFromYouTube(ytChannel);

        List<Video> insertedVideos = youtubeInternalService.insertMissingVideos(channel, allVideos, categoryMap);

        // TODO: For minor optimization could upsert only videos that are already inserted. (ALL - inserted)
        // TODO: Also can combine in one function insert + upsert logic
        youtubeInternalService.upsertVideoDetails(channel, allVideos, categoryMap);

        Long channelDbId = channel.getId();

        if (runTranscriptsSavingForAll) {
            allVideos.forEach(video -> publisher.publishEvent(new VideoDiscoveredEvent(video.getId(), channelDbId, regionCode)));
        } else {
            insertedVideos.forEach(video -> publisher.publishEvent(new VideoDiscoveredEvent(video.getYoutubeVideoId(), channelDbId, regionCode)));
        }

        return allVideos.stream().map(com.google.api.services.youtube.model.Video::getId).toList();
    }
}
