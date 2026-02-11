package com.youtube.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arxiv.oai")
public record ArxivOaiProps(String baseUrl, String userAgent) {}
