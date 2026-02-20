package com.data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oai.zenodo")
public record ZenodoOaiProps(
        String baseUrl,
        String metadataPrefix
) {}
