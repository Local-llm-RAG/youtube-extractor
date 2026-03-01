package com.data.embedding.dto;

import lombok.Builder;
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

    Integer chunkIndex;
    String chunkText;
    Integer spanStart;
    Integer spanEnd;
    List<Float> embedding;
}