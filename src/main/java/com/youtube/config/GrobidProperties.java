package com.youtube.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grobid")
public record GrobidProperties(
    String baseUrl,
    String fulltextEndpoint
) {}
