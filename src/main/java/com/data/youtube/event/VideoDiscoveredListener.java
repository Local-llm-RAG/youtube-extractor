package com.data.youtube.event;

import com.data.external.rest.pythonclient.RagSystemRestApiClient;
import com.data.jpa.dao.Video;
import com.data.jpa.dao.VideoTranscript;
import com.data.jpa.repository.VideoRepository;
import com.data.jpa.repository.VideoTranscriptRepository;
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
                    .map(language -> {
                        try {
                            return transcriptClient.transcript(youtubeVideoId, List.of(language));
                        } catch (Exception ex) {
                            log.error("Cannot find transcripts for video {} for language {}", youtubeVideoId, language, ex);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
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
                                        .categoryIds(List.of(event.categoryMap().get(languageWithTranscript.getKey()).getKey()))
                                        .categoryTitles(List.of(event.categoryMap().get(languageWithTranscript.getKey()).getValue()))
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
