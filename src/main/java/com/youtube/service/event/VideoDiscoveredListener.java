package com.youtube.service.event;

import com.youtube.external.rest.pythonclient.RagSystemRestApiClient;
import com.youtube.external.rest.pythonclient.dto.TranscriptResponse;
import com.youtube.jpa.dao.Video;
import com.youtube.jpa.dao.VideoTranscript;
import com.youtube.jpa.repository.VideoRepository;
import com.youtube.jpa.repository.VideoTranscriptRepository;
import com.youtube.jpa.repository.YouTubeRegionLanguageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.web.client.HttpClientErrorException;

import java.util.AbstractMap;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoDiscoveredListener {

    private final RagSystemRestApiClient transcriptClient;
    private final VideoRepository videoRepository;
    private final VideoTranscriptRepository transcriptRepository;

    @Async("transcriptExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVideoDiscovered(VideoDiscoveredEvent event) {
        String youtubeVideoId = event.youtubeVideoId();

        Video video = videoRepository.findByYoutubeVideoId(youtubeVideoId)
                .orElseThrow(() -> new IllegalStateException("Video not found in DB for youtubeVideoId=" + youtubeVideoId));

        if (video.isTranscriptPassed() && transcriptRepository.existsByVideoId(video.getId())) {
            log.error("[TRANSCRIPT][SKIP] already processed videoId={} videoDbId={}", youtubeVideoId, video.getId());
            return;
        }

        try {
            event.desiredLanguages().stream()
                    .map(language -> transcriptClient.transcript(youtubeVideoId, List.of(language)))
                    .map(resp -> new AbstractMap.SimpleEntry<>(resp.language(), resp.text()))
                    .filter(map -> {
                        if (Objects.nonNull(map.getValue()) || !map.getValue().isEmpty()) {
                            return true;
                        }
                        log.warn("Empty transcript for youtubeVideoId={}", youtubeVideoId);
                        video.setTranscriptPassed(false);
                        return false;
                    })
                    .forEach(languageWithTranscript -> {
                                log.info("[TRANSCRIPT][INSERT] videoId={} chars={}", youtubeVideoId, languageWithTranscript.getValue().length());
                                transcriptRepository.save(VideoTranscript.builder()
                                        .video(video)
                                        .transcriptText(languageWithTranscript.getValue())
                                        .categoryIds(event.categoryMap().get(languageWithTranscript.getKey()).keySet().stream().toList())
                                        .categoryTitles(event.categoryMap().get(languageWithTranscript.getKey()).values().stream().toList())
                                        .language(languageWithTranscript.getKey())
                                        .build());
                                video.setTranscriptPassed(true);
                            }
                    );

        } catch (HttpClientErrorException e) {
            video.setTranscriptPassed(false);
            int status = e.getStatusCode().value();
            if (status == 429) {
                log.error("[TRANSCRIPT][RATE_LIMIT] videoId={} status={} msg={}", youtubeVideoId, status, e.getMessage());
            } else {
                log.error("[TRANSCRIPT][HTTP_ERROR] videoId={} status={} msg={}", youtubeVideoId, status, e.getMessage());
            }
            throw e;

        } catch (Exception e) {
            video.setTranscriptPassed(false);
            log.error("[TRANSCRIPT][FAIL] videoId={} msg={}", youtubeVideoId, e.getMessage(), e);
        }
    }
}
