package com.data.openai.client;

public record TokenUsage(long promptTokens, long completionTokens, long totalTokens) {}
