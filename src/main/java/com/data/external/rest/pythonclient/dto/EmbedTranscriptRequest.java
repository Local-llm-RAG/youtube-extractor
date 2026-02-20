package com.data.external.rest.pythonclient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record EmbedTranscriptRequest(
        String text,
        String task,
        @JsonProperty("chunk_tokens") Integer chunkTokens,
        @JsonProperty("chunk_overlap") Integer chunkOverlap,
        Boolean normalize
) {}