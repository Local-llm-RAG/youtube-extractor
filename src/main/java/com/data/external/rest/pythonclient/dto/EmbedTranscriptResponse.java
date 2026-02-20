package com.data.external.rest.pythonclient.dto;

import java.util.List;

public record EmbedTranscriptResponse(
        String model,
        int dim,
        List<String> chunks,
        List<List<Integer>> spans,
        List<List<Float>> embeddings
) {}