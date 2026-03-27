package com.data.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oai.pubmed")
public record PubmedOaiProps(
        String baseUrl,
        String metadataPrefix,
        String set,
        String oaServiceUrl
) {}
