package com.youtube.gpt.estimation;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CostEstimate(long promptTokens, long averageCompletionTokens, BigDecimal averagePrice) {}
