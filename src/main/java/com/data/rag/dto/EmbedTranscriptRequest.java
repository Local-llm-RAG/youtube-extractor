package com.data.rag.dto;

import com.data.config.properties.EmbeddingProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record EmbedTranscriptRequest(
        String text,
        String task,
        @JsonProperty("chunk_tokens") Integer chunkTokens,
        @JsonProperty("chunk_overlap") Integer chunkOverlap,
        Boolean normalize
) {
    public static EmbedTranscriptRequest forPassage(String text, EmbeddingProperties props) {
        return EmbedTranscriptRequest.builder()
                .text(text)
                .task(EmbeddingTask.RETRIEVAL_PASSAGE.getValue())
                .chunkTokens(props.chunkSize())
                .chunkOverlap(props.overlap())
                .normalize(props.normalize())
                .build();
    }
}
