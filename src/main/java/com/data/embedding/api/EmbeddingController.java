package com.data.embedding.api;

import com.data.config.properties.EmbeddingProperties;
import com.data.rag.client.RagSystemRestApiClient;
import com.data.rag.dto.EmbedTranscriptRequest;
import com.data.rag.dto.EmbedTranscriptResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EmbeddingController {

    private final RagSystemRestApiClient client;
    private final EmbeddingProperties embeddingProps;

    public EmbeddingController(RagSystemRestApiClient client, EmbeddingProperties embeddingProps) {
        this.client = client;
        this.embeddingProps = embeddingProps;
    }

    @PostMapping("/embed")
    public EmbedTranscriptResponse embed(@RequestBody EmbedTranscriptRequest request) {
        EmbedTranscriptRequest withDefaults = new EmbedTranscriptRequest(
                request.text(),
                request.task() != null ? request.task() : "retrieval.passage",
                request.chunkTokens() != null ? request.chunkTokens() : embeddingProps.chunkSize(),
                request.chunkOverlap() != null ? request.chunkOverlap() : embeddingProps.overlap(),
                request.normalize() != null ? request.normalize() : embeddingProps.normalize()
        );

        return client.embedTranscript(withDefaults);
    }
}
