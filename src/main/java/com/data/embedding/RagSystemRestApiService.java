package com.data.embedding;

import com.data.embedding.dto.EmbeddingDto;
import com.data.external.rest.pythonclient.RagSystemRestApiClient;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptRequest;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagSystemRestApiService {
    private final RagSystemRestApiClient restApiClient;

    public EmbeddingDto getEmbeddingsForText(EmbedTranscriptRequest request) {
        long t0 = System.nanoTime();
        EmbedTranscriptResponse response = restApiClient.embedTranscript(request);
        long t1 = System.nanoTime();
        log.info("Embeddings {} ms", (t1 - t0) / 1_000_000);
        return EmbeddingDto.builder()
                .rawContent(request.text())
                .embeddingTask(request.task())
                .chunkTokens(request.chunkTokens())
                .chunkOverlap(request.chunkOverlap())
                .normalize(request.normalize())
                .embeddingModel(response.model())
                .dim(response.dim())
                .chunks(response.chunks())
                .spans(response.spans())
                .embeddings(response.embeddings())
                .build();
    }
}
