package com.data.config.properties;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rag.embedding.properties")
public record EmbeddingProperties(
        @NotNull
        @Max(1024) // For GTX-1060 6gb VRAM
        Integer chunkSize,
        @NotNull
        @DecimalMax("512") // half of the chunkSize
        Integer overlap,
        @NotNull
        Boolean normalize) {

}
