package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gpt")
public record GptProperties(
        String key,
        String model,
        Pricing pricing
) {
    public record Pricing(String inputUsdPer1m, String outputUsdPer1m) {}
}
