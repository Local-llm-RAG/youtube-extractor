package com.data.rag.dto;

public record ChatResponse(
        String answer,
        String modelPath,
        int historySize
) {}
