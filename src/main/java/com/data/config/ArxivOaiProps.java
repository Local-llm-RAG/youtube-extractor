package com.data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oai.arxiv")
public record ArxivOaiProps(String baseUrl, String userAgent) {}
