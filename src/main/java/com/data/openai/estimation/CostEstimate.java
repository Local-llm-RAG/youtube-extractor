package com.data.openai.estimation;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CostEstimate(long promptTokens, long averageCompletionTokens, BigDecimal averagePrice) {

    public static CostEstimate sum(CostEstimate a, CostEstimate b) {
        return CostEstimate.builder()
                .promptTokens(a.promptTokens() + b.promptTokens())
                .averageCompletionTokens(a.averageCompletionTokens() + b.averageCompletionTokens())
                .averagePrice(a.averagePrice().add(b.averagePrice()))
                .build();
    }
}
