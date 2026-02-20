package com.data.external.rest.pythonclient.dto;

import lombok.Getter;

@Getter
public enum EmbeddingTask {
    RETRIEVAL_QUERY("retrieval.query"),
    // For text -> embeddings
    RETRIEVAL_PASSAGE("retrieval.passage"),
    TEXT_MATCHING("text-matching");

    private final String value;
    EmbeddingTask(String value) {
        this.value = value;
    }

}
