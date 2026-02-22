package com.data.embedding;

import com.data.embedding.dto.EmbeddingDto;
import com.data.external.rest.pythonclient.RagSystemRestApiClient;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptRequest;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RagSystemRestApiService {
    private final RagSystemRestApiClient restApiClient;

    public EmbeddingDto getEmbeddingsForText(EmbedTranscriptRequest request) {
        EmbedTranscriptResponse response = restApiClient.embedTranscript(request);

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
