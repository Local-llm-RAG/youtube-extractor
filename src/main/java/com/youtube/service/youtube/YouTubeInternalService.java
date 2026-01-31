package com.youtube.service.youtube;

import com.google.api.services.youtube.model.*;
import com.youtube.jpa.dao.ChannelDao;
import com.youtube.jpa.dao.Video;
import com.youtube.jpa.repository.ChannelRepository;
import com.youtube.jpa.repository.VideoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YouTubeInternalService {

    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;

    @Transactional
    public ChannelDao upsertChannelFromYouTube(Channel ytChannel) {
        ChannelSnippet snippet = ytChannel.getSnippet();

        ChannelDao incoming = buildFromYoutubeChannel(ytChannel, snippet);

        return channelRepository.findByYoutubeChannelId(incoming.getYoutubeChannelId())
                .map(existing -> {
                    existing.setName(incoming.getName());
                    existing.setDescription(incoming.getDescription());
                    existing.setCountry(incoming.getCountry());
                    return channelRepository.save(existing);
                })
                .orElseGet(() -> channelRepository.save(incoming));
    }

    public Set<String> findExistingVideoIds(Collection<String> youtubeVideoIds) {
        return videoRepository.findAllByYoutubeVideoIdIn(List.copyOf(youtubeVideoIds))
                .stream()
                .map(Video::getYoutubeVideoId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public List<Video> insertMissingVideos(
            ChannelDao channel,
            List<com.google.api.services.youtube.model.Video> ytVideos) {
        Set<String> existing = findExistingVideoIds(ytVideos.stream().map(com.google.api.services.youtube.model.Video::getId).collect(Collectors.toSet()));

        List<com.google.api.services.youtube.model.Video> toInsert = ytVideos.stream()
                .filter(video -> !existing.contains(video.getId()))
                .toList();

        return upsertVideoDetails(channel, toInsert);
    }

    @Transactional
    public List<Video> upsertVideoDetails(
            ChannelDao channel,
            List<com.google.api.services.youtube.model.Video> ytVideos) {
        List<Video> databaseVideos = ytVideos.stream()
                .map(video -> buildVideoDao(video, channel))
                .toList();
        return videoRepository.saveAll(databaseVideos);
    }

    private Video buildVideoDao(com.google.api.services.youtube.model.Video video, ChannelDao channel) {

        Video videoDao = videoRepository.findByYoutubeVideoId(video.getId())
                .orElseGet(Video::new);

        videoDao.setYoutubeVideoId(video.getId());
        videoDao.setChannelDao(channel);

        var sn = video.getSnippet();
        if (sn != null) {
            videoDao.setTitle(sn.getTitle());
            videoDao.setDescription(sn.getDescription());
            videoDao.setDefaultLanguage(sn.getDefaultLanguage());

            videoDao.setPublishedAt(sn.getPublishedAt() != null ? OffsetDateTime.parse(sn.getPublishedAt().toStringRfc3339()) : null);

            videoDao.setTags(sn.getTags() == null ? null : List.copyOf(sn.getTags()));
        }

        VideoStatus st = video.getStatus();
        if (st != null) {
            videoDao.setMadeForKids(st.getMadeForKids());
            videoDao.setLicense(st.getLicense());
        }

        VideoTopicDetails td = video.getTopicDetails();
        videoDao.setTopicCategories(td != null && td.getTopicCategories() != null ? List.copyOf(td.getTopicCategories()) : null);

        return videoDao;
    }

    public List<ChannelDao> findAllChannels() {
        return channelRepository.findAll();
    }

    private static ChannelDao buildFromYoutubeChannel(Channel ytChannel, ChannelSnippet snippet) {
        return ChannelDao.builder()
                .youtubeChannelId(ytChannel.getId())
                .name(snippet.getTitle())
                .description(snippet.getDescription())
                .country(snippet.getCountry())
                .build();
    }

    public List<Video> findAllVideosForChannel(ChannelDao channel) {
        return videoRepository.findVideosByChannelDao(channel);
    }

    public List<String> fetchRegionsForLanguage(String language) {
        return null;
    }
}
