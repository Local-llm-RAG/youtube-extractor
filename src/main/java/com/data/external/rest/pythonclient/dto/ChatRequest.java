package com.data.external.rest.pythonclient.dto;

import java.util.List;
import java.util.Map;

public record ChatRequest(
        String message,
        List<Map<String, String>> history,
        int maxTokens,
        double temperature,
        double topP,
        double repeatPenalty
) { }
