package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oai.arxiv")
public record ArxivOaiProps(String baseUrl, String userAgent) {}
