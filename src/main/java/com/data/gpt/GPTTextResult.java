package com.data.gpt;

import com.openai.models.completions.CompletionUsage;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record GPTTextResult(String text, CompletionUsage usage, BigDecimal actualUsd) {}
