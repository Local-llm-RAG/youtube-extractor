package com.data.openai.client;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record GPTTextResult(String text, TokenUsage usage, BigDecimal actualUsd) {}
