package com.data.embedding.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class EmbeddingDto {
    String rawContent;
    String embeddingTask;
    Integer chunkTokens;
    Integer chunkOverlap;
    Boolean normalize;
    String embeddingModel;
    int dim;
    List<String> chunks;
    List<List<Integer>> spans; // List of (List of 2 elements) - start and end char
    List<List<Float>> embeddings;
}
