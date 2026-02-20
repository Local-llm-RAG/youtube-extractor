package com.data.embedding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record QdrantChunk(
        String id,
        List<Double> vector, // TODO Can be optimized with float
        Map<String, Object> payload
) {

}