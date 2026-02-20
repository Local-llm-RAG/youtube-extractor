package com.data.embedding;

import com.data.config.EmbeddingProperties;
import com.data.external.rest.pythonclient.RagSystemRestApiClient;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptRequest;
import com.data.external.rest.pythonclient.dto.EmbeddingTask;
import com.data.youtube.YouTubeInternalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class QdrantProcessorService implements Job {

    private final YouTubeInternalService youTubeInternalService;
    private final QdrantGrpsClient qdrantGrpsClient;
    private final RagSystemRestApiClient pythonRestApiClient;
    private final EmbeddingProperties embeddingProperties;

    @Override
    public String getName() {
        return "QdrantProcessorService Job";
    }

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
