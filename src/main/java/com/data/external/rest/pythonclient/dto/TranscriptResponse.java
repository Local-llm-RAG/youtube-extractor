package com.data.external.rest.pythonclient.dto;

public record TranscriptResponse(
        String videoId,
        String language,
        String text
) {}
