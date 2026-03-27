package com.data.rag.dto;

public record TranscriptResponse(
        String videoId,
        String language,
        String text
) {}
