package com.data.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rag.model.properties")
public record ModelProperties(

        @NotNull
        @Min(1)
        @Max(16384)
        Integer maxTokens,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("2.0")
        Double temperature,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double topP,

        @NotNull
        @DecimalMin("1.0")
        @DecimalMax("2.0")
        Double repeatPenalty
) {}
