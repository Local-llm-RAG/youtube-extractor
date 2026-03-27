package com.data.rag.client;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class RagSystemWebFluxClient {

    private final WebClient webClient;

    public RagSystemWebFluxClient(WebClient pythonEmbeddingWebClient) {
        this.webClient = pythonEmbeddingWebClient;
    }
}
