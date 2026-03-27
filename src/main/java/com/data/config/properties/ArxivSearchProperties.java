package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arxiv")
public record ArxivSearchProperties(String searchUrl) {}
