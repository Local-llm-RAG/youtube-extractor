package com.data.embedding;

import com.data.external.rest.pythonclient.RagSystemRestApiClient;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptRequest;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EmbeddingController {

    private final RagSystemRestApiClient client;

    public EmbeddingController(RagSystemRestApiClient client) {
        this.client = client;
    }

    //For debug
    @PostMapping("/embed")
    public EmbedTranscriptResponse embed(@RequestBody EmbedTranscriptRequest request) {
        EmbedTranscriptRequest withDefaults = new EmbedTranscriptRequest(
                request.text(),
                request.task() != null ? request.task() : "retrieval.passage",
                request.chunkTokens() != null ? request.chunkTokens() : 1024,
                request.chunkOverlap() != null ? request.chunkOverlap() : 128,
                request.normalize() != null ? request.normalize() : Boolean.TRUE
        );

        return client.embedTranscript(withDefaults);
    }
}