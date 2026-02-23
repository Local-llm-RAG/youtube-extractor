package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grobid")
public record GrobidProperties(
    String baseUrl,
    String fulltextEndpoint
) {}
