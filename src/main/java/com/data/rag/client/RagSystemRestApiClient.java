package com.data.rag.client;

import com.data.shared.exception.TranscriptRateLimitedException;
import com.data.rag.dto.ChatRequest;
import com.data.rag.dto.ChatResponse;
import com.data.rag.dto.TranscriptResponse;
import com.data.rag.dto.EmbedTranscriptRequest;
import com.data.rag.dto.EmbedTranscriptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.List;

@Component
public class RagSystemRestApiClient {
    private final RestClient restClient;

    public RagSystemRestApiClient(@Qualifier("ragRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ChatResponse chat(ChatRequest request) {
        return restClient.post()
                .uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);
    }

//    @RateLimiter(name = "transcript")
//    @Retry(name = "transcript")
//    @CircuitBreaker(name = "transcript429")
    public TranscriptResponse transcript(String videoId, List<String> languages) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        UriBuilder b = uriBuilder.path("/youtube/transcript")
                                .queryParam("video_id", videoId);
                        for (String lang : languages) b.queryParam("languages", lang);
                        return b.build();
                    })
                    .retrieve()
                    .body(TranscriptResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                throw new TranscriptRateLimitedException(e);
            }
            throw e;
        }
    }

    public EmbedTranscriptResponse embedTranscript(EmbedTranscriptRequest req) {
        return restClient.post()
                .uri("/embed_transcript")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(EmbedTranscriptResponse.class);
    }
}
