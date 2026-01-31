package com.youtube.service.qdrant;

import com.youtube.config.EmbeddingProperties;
import com.youtube.external.rest.pythonclient.RagSystemRestApiClient;
import com.youtube.external.rest.qdrant.QdrantGrpsClient;
import com.youtube.external.webflux.RagSystemWebFluxClient;
import com.youtube.external.rest.pythonclient.dto.EmbedTranscriptRequest;
import com.youtube.external.rest.pythonclient.dto.EmbeddingTask;
import com.youtube.jpa.dao.Video;
import com.youtube.service.youtube.YouTubeInternalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class QdrantProcessorService implements Job {

    private final YouTubeInternalService youTubeInternalService;
    private final QdrantGrpsClient qdrantGrpsClient;
    private final RagSystemRestApiClient pythonRestApiClient;
    private final EmbeddingProperties embeddingProperties;

    @Override
    public void execute(JobExecution execution) {
//        youTubeInternalService.findAllChannels()
//                .stream()
//                .forEach(channel -> {
//                    List<Video> videosWithTranscript = youTubeInternalService.findAllVideosForChannel(channel)
//                            .parallelStream()
//                            .filter(Video::isTranscriptPassed)
//                            .filter(vid -> Objects.nonNull(vid.getVideoTranscript()))
//                            .toList();
//                    videosWithTranscript
//                            .stream()
//                            .map(video ->
//                                    new AbstractMap.SimpleEntry<>(video, pythonRestApiClient.embedTranscript(buildEmbedTranscriptRequest(video.getVideoTranscript().getTranscriptText()))))
//                            .forEach(videoWithEmbeddings ->
//                                    qdrantGrpsClient.insertPoints(channel, videoWithEmbeddings.getKey(), videoWithEmbeddings.getValue(), "bulgarian")
//                            );
//                });
    }

    private EmbedTranscriptRequest buildEmbedTranscriptRequest(String transcriptText) {
        return EmbedTranscriptRequest.builder()
                .text(transcriptText)
                .task(EmbeddingTask.RETRIEVAL_PASSAGE.getValue())
                .chunkTokens(embeddingProperties.chunkSize())
                .chunkOverlap(embeddingProperties.overlap())
                .normalize(embeddingProperties.normalize())
                .build();
    }


}
