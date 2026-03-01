package com.data.embedding;

import com.data.embedding.dto.EmbeddingDto;
import com.data.external.rest.pythonclient.RagSystemRestApiClient;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptRequest;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagSystemRestApiService {
    private final RagSystemRestApiClient restApiClient;

    public List<EmbeddingDto> getEmbeddingsForText(EmbedTranscriptRequest request) {

        long t0 = System.nanoTime();
        EmbedTranscriptResponse response = restApiClient.embedTranscript(request);
        long t1 = System.nanoTime();

        log.info("Embeddings {} ms", (t1 - t0) / 1_000_000);

        List<EmbeddingDto> result = new ArrayList<>();

        List<String> chunks = response.chunks();
        List<List<Integer>> spans = response.spans();
        List<List<Float>> vectors = response.embeddings();

        for (int i = 0; i < chunks.size(); i++) {

            List<Integer> span = spans.get(i);

            result.add(
                    EmbeddingDto.builder()
                            .rawContent(request.text())
                            .embeddingTask(request.task())
                            .chunkTokens(request.chunkTokens())
                            .chunkOverlap(request.chunkOverlap())
                            .normalize(request.normalize())
                            .embeddingModel(response.model())
                            .dim(response.dim())
                            .chunkIndex(i)
                            .chunkText(chunks.get(i))
                            .spanStart(span.get(0))
                            .spanEnd(span.get(1))
                            .embedding(vectors.get(i))
                            .build()
            );
        }

        return result;
    }
}
